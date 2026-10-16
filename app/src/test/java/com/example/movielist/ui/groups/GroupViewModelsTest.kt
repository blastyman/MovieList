package com.example.movielist.ui.groups

import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.GroupMatch
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.friends.FakeFriends
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val me = UserSummary("me", "me", null, FriendshipState.NONE)
    private val friends = FakeFriends().apply { (1..12).forEach { add("$it", FriendshipState.FRIENDS) } }
    private val groups = FakeGroups(me) { id -> UserSummary(id, "user$id", null, FriendshipState.FRIENDS) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pickingStopsAtTenIncludingYou() =
        runTest(dispatcher) {
            val viewModel = CreateGroupViewModel(friends, groups)
            advanceUntilIdle()
            assertEquals(12, viewModel.uiState.friends.size)
            assertEquals(1, viewModel.uiState.memberCount) // just you

            viewModel.uiState.friends.forEach(viewModel::toggle)

            assertEquals(MAX_GROUP_MEMBERS, viewModel.uiState.memberCount)
            assertTrue(viewModel.uiState.isFull)
            assertNotNull(viewModel.uiState.message) // told why the rest weren't picked

            // Unpicking one frees a spot again.
            viewModel.toggle(viewModel.uiState.friends.first())
            assertEquals(MAX_GROUP_MEMBERS - 1, viewModel.uiState.memberCount)
            assertFalse(viewModel.uiState.isFull)
        }

    @Test
    fun creatingNeedsANameAndOpensTheNewGroup() =
        runTest(dispatcher) {
            val viewModel = CreateGroupViewModel(friends, groups)
            advanceUntilIdle()
            viewModel.toggle(viewModel.uiState.friends[1])
            viewModel.toggle(viewModel.uiState.friends[0])

            assertFalse(viewModel.uiState.canCreate)
            viewModel.create()
            advanceUntilIdle()
            assertNull(viewModel.uiState.createdGroupId)

            viewModel.onNameChange("  Movie night  ")
            viewModel.create()
            advanceUntilIdle()

            val group = groups.groups.getValue(viewModel.uiState.createdGroupId!!)
            assertEquals("Movie night", group.name)
            // Owner first, then the friends in the order they're listed, not the order they were ticked.
            assertEquals(listOf("me", "1", "2"), group.members.map { it.id })
        }

    @Test
    fun aFailedCreateSaysWhyAndStaysOnTheScreen() =
        runTest(dispatcher) {
            groups.failWith = IOException("Offline")
            val viewModel = CreateGroupViewModel(friends, groups)
            advanceUntilIdle()
            viewModel.onNameChange("Club")
            viewModel.create()
            advanceUntilIdle()

            assertNull(viewModel.uiState.createdGroupId)
            assertFalse(viewModel.uiState.isCreating)
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.message)
        }

    @Test
    fun groupScreenLoadsMembersAndSharedMovies() =
        runTest(dispatcher) {
            val group = groups.create("Club", listOf("1"))
            val movie = Movie(7, "Heat", "", null, emptyList())
            groups.matches[group.id] = listOf(GroupMatch(movie, Instant.EPOCH))
            val viewModel = GroupViewModel(groups, friends)

            viewModel.load(group.id)
            advanceUntilIdle()

            assertEquals(listOf("me", "1"), viewModel.uiState.group?.members?.map { it.id })
            assertEquals(listOf(movie), viewModel.uiState.matches.map { it.movie })
        }

    @Test
    fun theAddPickerOffersOnlyFriendsNotInTheGroupAndClosesWhenFull() =
        runTest(dispatcher) {
            val group = groups.create("Club", (1..8).map { "$it" }) // 9 people
            val viewModel = GroupViewModel(groups, friends)
            viewModel.load(group.id)
            advanceUntilIdle()

            viewModel.startAdding()
            advanceUntilIdle()
            assertEquals(listOf("9", "10", "11", "12"), viewModel.uiState.addable?.map { it.id })

            viewModel.add(viewModel.uiState.addable!!.first())
            advanceUntilIdle()

            assertEquals(MAX_GROUP_MEMBERS, viewModel.uiState.group?.members?.size)
            assertNull(viewModel.uiState.addable) // full: nothing more to add

            viewModel.startAdding()
            assertNull(viewModel.uiState.addable)
            assertEquals(fullMessage(), viewModel.uiState.message)
        }

    @Test
    fun removingAsksFirstAndLeavingGoesBack() =
        runTest(dispatcher) {
            val group = groups.create("Club", listOf("1", "2"))
            val viewModel = GroupViewModel(groups, friends)
            viewModel.load(group.id)
            advanceUntilIdle()
            val two = viewModel.uiState.group!!.members.last()

            viewModel.askRemove(two)
            assertEquals(two, viewModel.uiState.confirmRemove)
            viewModel.confirmRemove()
            advanceUntilIdle()
            assertEquals(listOf("me", "1"), viewModel.uiState.group?.members?.map { it.id })

            viewModel.askLeave()
            viewModel.confirmLeave(me.id)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.left)
            assertTrue(groups.groups.isEmpty())
        }

    @Test
    fun theOwnerCanDisbandInsteadOfLeaving() =
        runTest(dispatcher) {
            val group = groups.create("Club", listOf("1"))
            val viewModel = GroupViewModel(groups, friends)
            viewModel.load(group.id)
            advanceUntilIdle()

            viewModel.askLeave()
            viewModel.confirmDisband(me.id)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.confirmLeave)
            assertTrue(viewModel.uiState.left)
            assertTrue(groups.groups.isEmpty())
        }

    @Test
    fun aFailedRenameBringsTheOldNameBack() =
        runTest(dispatcher) {
            val group = groups.create("Club", emptyList())
            val viewModel = GroupViewModel(groups, friends)
            viewModel.load(group.id)
            advanceUntilIdle()

            groups.failWith = IOException("Offline")
            viewModel.startRename()
            viewModel.onNameDraftChange("Movie club")
            viewModel.saveRename()
            assertEquals("Movie club", viewModel.uiState.group?.name) // shown at once
            advanceUntilIdle()

            assertEquals("Club", viewModel.uiState.group?.name)
            assertNotNull(viewModel.uiState.message)
        }
}
