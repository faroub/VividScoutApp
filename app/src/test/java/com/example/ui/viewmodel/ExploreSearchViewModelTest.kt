package com.example.ui.viewmodel

import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scoutRepository: ScoutRepository
    private lateinit var gemRepository: ScoutGemRepository
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scoutRepository = mock(ScoutRepository::class.java)
        gemRepository = mock(ScoutGemRepository::class.java)

        `when`(scoutRepository.allSpotsFlow).thenReturn(flowOf(emptyList()))
        `when`(scoutRepository.savedSpotsFlow).thenReturn(flowOf(emptyList()))
        `when`(scoutRepository.discoveredSpotsFlow).thenReturn(flowOf(emptyList()))
        `when`(gemRepository.recentSearchesFlow).thenReturn(flowOf(emptyList()))

        viewModel = ExploreViewModel(scoutRepository, gemRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSearchQuery_emptyValue_maintainsFocusedEmptyStateAndQueryText() = runTest {
        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()

        val currentQuery = viewModel.searchQuery.value
        assertEquals("", currentQuery)
    }

    @Test
    fun tappingSuggestion_updatesSearchQueryTextAndRecordsQuery() = runTest {
        val suggestionQuery = "historic sites"

        viewModel.setSearchQuery(suggestionQuery)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("historic sites", viewModel.searchQuery.value)
        verify(gemRepository).recordRecentSearch("historic sites")
    }

    @Test
    fun clearAllRecentSearches_invokesRepositoryClear() = runTest {
        viewModel.clearAllRecentSearches()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(gemRepository).clearAllRecentSearches()
    }

    @Test
    fun deleteRecentSearch_invokesRepositoryDelete() = runTest {
        viewModel.deleteRecentSearch("search_123")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(gemRepository).removeRecentSearch("search_123")
    }
}
