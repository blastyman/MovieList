# MovieList Social — Server Setup Plan (VPS)

How to get from "no server" to "the Android app talks to `https://api.<your-domain>`" on a
cheap VPS. It goes with [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) (it replaces the
managed-hosting option in Phase 11).

## 1. What to buy

### Short answer
Yes — a ~$5/month VPS is plenty for a thesis project. Ktor + PostgreSQL + Caddy together
use well under 1 GB of RAM at this scale.

| Item | Recommendation | Approx. cost |
|---|---|---|
| **VPS** | OVHcloud **VPS-1** (the cheapest tier), Ubuntu **24.04 LTS**, European datacenter (Gravelines / Strasbourg / Frankfurt — closest to Greece) | ~$5–7 / month |
| **Domain** | Any cheap domain (`.xyz`, `.dev`, `.me`…) from Namecheap / Porkbun / Cloudflare, **or** a free subdomain from DuckDNS | $0–12 / year |
| Backups add-on | Optional (OVH "automated backup"); section 9 covers a free alternative | ~$1–2 / month |

Minimum specs if you buy elsewhere: **1 vCPU, 2 GB RAM, 20 GB SSD**, Ubuntu 24.04, IPv4 address.

**Why you need a domain:** Android blocks plain `http://` by default, and free HTTPS
certificates (Let's Encrypt) need a domain name, not a bare IP. A free DuckDNS name
(`movielist.duckdns.org`) works fine if you don't want to pay.

### Cheaper or free alternatives
- **GitHub Student Developer Pack** (free with your university email): includes cloud credits
  (historically DigitalOcean credit) and a free domain for a year (Namecheap `.me`). Check the
  current offers — this may make the whole setup free for the duration of the thesis.
- **Azure for Students**: ~$100 free credit, no credit card required.
- **Hetzner CX22**: similar price and specs to OVH, also in Germany/Finland.

Any of these work with the steps below as long as you pick Ubuntu 24.04.

> Prices change; check the provider's current page before buying. Pay monthly, not yearly,
> so you can cancel after your defense.

### During checkout
- Image: **Ubuntu 24.04 LTS** (no control panel, no preinstalled apps)
- **Add your SSH public key** if the order form allows it (step 2 shows how to create one)
- Note the **IPv4 address** and the initial username (`ubuntu` on OVH) from the confirmation email

## 2. Create an SSH key (on your Windows PC)

In PowerShell:

```bash
ssh-keygen -t ed25519 -C "movielist-vps"
```

Press Enter to accept `C:\Users\<you>\.ssh\id_ed25519`, and set a passphrase.
Your **public** key is `~/.ssh/id_ed25519.pub` — that's the one you paste into OVH.
Never share the file without `.pub`.

First login:

```bash
ssh ubuntu@<VPS_IP>
```

Optional convenience — add to `C:\Users\<you>\.ssh\config`:

```
Host movielist
    HostName <VPS_IP>
    User ubuntu
    IdentityFile ~/.ssh/id_ed25519
```

Then `ssh movielist` is enough.

## 3. Secure the server (first 20 minutes)

Run on the VPS:

```bash
# 1. Update everything
sudo apt update && sudo apt full-upgrade -y
sudo timedatectl set-timezone Europe/Athens

# 2. Automatic security updates
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades

# 3. Firewall: only SSH + web
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# 4. Block SSH brute-force attempts
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
```

Disable password and root login (do this **only after** you confirmed key login works):

```bash
sudo nano /etc/ssh/sshd_config.d/99-hardening.conf
```

```
PasswordAuthentication no
PermitRootLogin no
KbdInteractiveAuthentication no
```

```bash
sudo systemctl restart ssh
```

Keep your current SSH window open and test from a **new** window that you can still log in
before closing it.

## 4. Install Docker

Official Docker repository (not the old `docker.io` Ubuntu package):

```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Log out and back in, then check: `docker run --rm hello-world`.

> ⚠️ **Docker bypasses `ufw`.** Any `ports: "5432:5432"` in a compose file is reachable from
> the whole internet even though ufw says it's blocked. In production, **only Caddy publishes
> ports (80/443)**. Postgres and the backend have no `ports:` at all — they talk over the
> internal Docker network.

## 5. Point your domain at the server

At your domain registrar (or DuckDNS), create an **A record**:

| Type | Name | Value |
|---|---|---|
| A | `api` | `<VPS_IP>` |

So the API will live at `https://api.<your-domain>`. Check it resolved with
`nslookup api.<your-domain>` before continuing (can take a few minutes).

## 6. Production layout on the server

```
/opt/movielist/
├── repo/                    # git clone of your project
├── docker-compose.prod.yml
├── Caddyfile
├── .env                     # secrets — chmod 600, never committed
└── backups/                 # nightly database dumps
```

```
Internet ──443──▶ Caddy (HTTPS, auto certificates)
                    │  reverse_proxy (also handles WebSockets)
                    ▼
                 backend:8080 (Ktor) ───▶ postgres:5432
                 (internal Docker network only)
```

### `docker-compose.prod.yml`
```yaml
services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: movielist
      POSTGRES_USER: movielist
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U movielist"]
      interval: 10s
      retries: 5
    # no ports: — not reachable from outside

  backend:
    build: ./repo/backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      APP_ENV: production   # every variable below is then required; no dev fallbacks
      DB_URL: jdbc:postgresql://postgres:5432/movielist
      DB_USER: movielist
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      TMDB_TOKEN: ${TMDB_TOKEN}
      PORT: 8080
      UPLOAD_DIR: /data/avatars
    volumes:
      - avatars:/data/avatars   # profile pictures survive rebuilds; back this up with the database
    # no ports: — only Caddy reaches it

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - backend

volumes:
  pgdata:
  avatars:
  caddy_data:
  caddy_config:
```

### `Caddyfile`
```
api.<your-domain> {
    encode gzip
    reverse_proxy backend:8080
}
```

Caddy gets and renews the Let's Encrypt certificate automatically, and proxies the `/ws`
WebSocket with no extra config.

### `.env`
```bash
DB_PASSWORD=<long random string>
JWT_SECRET=<long random string>
TMDB_TOKEN=<your NEW TMDB read token>
```

Generate random values on the server with `openssl rand -base64 48`.
Then `chmod 600 /opt/movielist/.env`.

## 7. First deployment

```bash
sudo mkdir -p /opt/movielist && sudo chown $USER:$USER /opt/movielist
cd /opt/movielist
git clone https://github.com/<you>/MovieList.git repo   # private repo: use a deploy key or a fine-grained token
# create docker-compose.prod.yml, Caddyfile, .env as above
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

Check from your PC:

```bash
curl https://api.<your-domain>/health
```

Then set the Android release `API_BASE_URL` to `https://api.<your-domain>/`.

## 8. Updating the server after code changes

Simple manual way (fine for a thesis):

```bash
cd /opt/movielist/repo && git pull
cd .. && docker compose -f docker-compose.prod.yml up -d --build backend
```

Flyway runs new migrations automatically when the backend starts.

**Optional upgrade — automatic deploys:** a GitHub Actions workflow that, on push to `main`,
builds the backend image, pushes it to GitHub Container Registry, then SSHes into the VPS and
runs `docker compose pull && docker compose up -d`. A nice "CI/CD" section for the thesis,
but not required.

## 9. Backups

Your users, chats and comments live only in Postgres — back it up.

`/opt/movielist/backup.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd /opt/movielist
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U movielist movielist | gzip > "backups/movielist-$(date +%F).sql.gz"
find backups -name '*.sql.gz' -mtime +7 -delete   # keep 7 days
```

```bash
chmod +x /opt/movielist/backup.sh
crontab -e
# add:
0 3 * * * /opt/movielist/backup.sh
```

A backup on the same server doesn't help if the server dies, so copy one off now and then
from your PC:

```bash
scp movielist:/opt/movielist/backups/*.sql.gz .
```

(Or pay for the provider's snapshot/backup add-on.)

Restore test (do it once so you know it works):
```bash
gunzip -c backups/movielist-YYYY-MM-DD.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U movielist movielist
```

## 10. Monitoring

- **Logs:** `docker compose -f docker-compose.prod.yml logs -f --tail=200 backend`
- **Resource use:** `docker stats`, `htop`, `df -h`
- **Uptime alerts:** free UptimeRobot (or self-hosted Uptime Kuma) checking
  `https://api.<your-domain>/health` every 5 minutes, emails you if it's down
- **Log size:** add to `/etc/docker/daemon.json` so logs don't fill the disk, then
  `sudo systemctl restart docker`:
  ```json
  { "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
  ```

## 11. Checklist

- [ ] VPS bought (Ubuntu 24.04), IP noted
- [ ] SSH key login works; password + root login disabled
- [ ] ufw allows only 22, 80, 443; fail2ban + unattended-upgrades on
- [ ] Docker + compose plugin installed
- [ ] `api.<domain>` A record points to the VPS
- [ ] `.env` created with strong secrets and `chmod 600`
- [ ] `docker compose up` → `curl https://api.<domain>/health` returns OK
- [ ] Postgres **not** reachable from outside (`nc -zv <VPS_IP> 5432` from your PC must fail)
- [ ] Nightly backup cron running; restore tested once
- [ ] Uptime monitor configured
- [ ] Android release build points to the HTTPS URL

## 12. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Caddy logs "challenge failed" | DNS not pointing to the VPS yet, or port 80 blocked by ufw / the provider's firewall |
| App: "CLEARTEXT communication not permitted" | Release build is using `http://` — use the HTTPS domain |
| Backend restarts in a loop | Wrong `DB_PASSWORD` / env var name — check `docker compose logs backend` |
| Chat connects then drops after ~1 min | Add WebSocket ping frames in Ktor (`pingPeriod = 30.seconds`) |
| Locked out of SSH | Use the OVH control panel's KVM / rescue console |

---

## Prompt for Claude Code

```
Read docs/SERVER_SETUP.md and docs/IMPLEMENTATION_PLAN.md. Add a deploy/ folder with
docker-compose.prod.yml, a Caddyfile template, .env.example (no real secrets), and
backup.sh exactly as described in SERVER_SETUP.md §6 and §9, plus a multi-stage
Dockerfile for backend/ (Gradle build stage, slim JRE 21 runtime stage, non-root user).
Add WebSocket ping configuration to the Ktor server. Make sure .env is gitignored. Then give
me the exact commands to run on a fresh Ubuntu 24.04 VPS for the first deploy, using
api.<domain> as a placeholder.
```
