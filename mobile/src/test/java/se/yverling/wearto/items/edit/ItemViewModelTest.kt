package se.yverling.wearto.items.edit

import android.app.Application
import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.content.SharedPreferences
import assertk.assert
import assertk.assertions.isEqualTo
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import se.yverling.wearto.core.db.DatabaseClient
import se.yverling.wearto.core.entities.Item
import se.yverling.wearto.core.entities.ItemWithProject
import se.yverling.wearto.core.entities.Project
import se.yverling.wearto.items.edit.ItemViewModel.Events.*
import se.yverling.wearto.test.RxRule
import se.yverling.wearto.test.valueIsEqualTo
import se.yverling.wearto.test.whenever

private const val UUID = "UUID"

private const val ITEM_NAME = "Item1"

private const val FIRST_PROJECT_ID = 1L
private const val FIRST_PROJECT_NAME = "Project1"
private const val FIRST_PROJECT_COLOR = 0

private const val SECOND_PROJECT_ID = 2L
private const val SECOND_PROJECT_NAME = "Project2"
private const val SECOND_PROJECT_COLOR = 1

class ItemViewModelTest {
    @get:Rule
    var mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    var liveDataRule = InstantTaskExecutorRule()

    @get:Rule
    var rxRule: RxRule = RxRule()

    @Mock
    lateinit var applicationMock: Application
    @Mock
    lateinit var databaseClientMock: DatabaseClient
    @Mock
    lateinit var sharedPreferencesMock: SharedPreferences

    lateinit var viewModel: ItemViewModel

    @Before
    fun setup() {
        val testProjects = listOf(
                Project(FIRST_PROJECT_ID, FIRST_PROJECT_NAME, FIRST_PROJECT_COLOR),
                Project(SECOND_PROJECT_ID, SECOND_PROJECT_NAME, SECOND_PROJECT_COLOR)
        )

        whenever(databaseClientMock.findAllProjects()).thenReturn(Single.just(testProjects))

        viewModel = ItemViewModel(applicationMock, databaseClientMock, sharedPreferencesMock)
    }

    @Test
    fun shouldEditItemSuccessfully() {
        val itemWithProject = ItemWithProject()
        itemWithProject.item = Item(UUID, ITEM_NAME, FIRST_PROJECT_ID)
        itemWithProject.project = Project(FIRST_PROJECT_ID, FIRST_PROJECT_NAME, FIRST_PROJECT_COLOR)

        whenever(databaseClientMock.findItemWithProjectByUuid(UUID)).thenReturn(Single.just(itemWithProject))

        viewModel.edit(UUID)

        assert(viewModel.name).valueIsEqualTo(ITEM_NAME)
        assert(viewModel.projectName).valueIsEqualTo(FIRST_PROJECT_NAME)
    }

    @Test
    fun shouldSaveNewItemSuccessfully() {
        viewModel.name.set(ITEM_NAME)
        viewModel.projectName.set(FIRST_PROJECT_NAME)

        whenever(databaseClientMock.saveItem(ITEM_NAME, FIRST_PROJECT_NAME)).thenReturn(Completable.complete())

        viewModel.save()

        verify(databaseClientMock).saveItem(ITEM_NAME, FIRST_PROJECT_NAME)
        assert(viewModel.events.value).isEqualTo(FINISH_ACTIVITY_EVENT)
    }

    @Test
    fun shouldShouldErrorMessageWhenItemCouldNotBeSave() {
        viewModel.name.set(ITEM_NAME)
        viewModel.projectName.set(FIRST_PROJECT_NAME)

        whenever(databaseClientMock.saveItem(ITEM_NAME, FIRST_PROJECT_NAME)).thenReturn(Completable.error(RuntimeException()))

        viewModel.save()

        verify(databaseClientMock).saveItem(ITEM_NAME, FIRST_PROJECT_NAME)
        assert(viewModel.events.value).isEqualTo(SHOW_SAVE_FAILED_DIALOG_EVENT)
    }

    @Test
    fun shouldSaveExistingItemSuccessfully() {
        viewModel.uuid.set(UUID)
        viewModel.name.set(ITEM_NAME)
        viewModel.projectName.set(FIRST_PROJECT_NAME)

        whenever(databaseClientMock
                .updateItem(UUID, ITEM_NAME, FIRST_PROJECT_NAME))
                .thenReturn(Completable.complete())

        viewModel.save()

        verify(databaseClientMock).updateItem(UUID, ITEM_NAME, FIRST_PROJECT_NAME)
        assert(viewModel.events.value).isEqualTo(FINISH_ACTIVITY_EVENT)
    }

    @Test
    fun shouldShowErrorMessageWhenSavingExistingItemFailed() {
        viewModel.uuid.set(UUID)
        viewModel.name.set(ITEM_NAME)
        viewModel.projectName.set(FIRST_PROJECT_NAME)

        whenever(databaseClientMock
                .updateItem(UUID, ITEM_NAME, FIRST_PROJECT_NAME))
                .thenReturn(Completable.error(RuntimeException()))

        viewModel.save()

        verify(databaseClientMock).updateItem(UUID, ITEM_NAME, FIRST_PROJECT_NAME)
        assert(viewModel.events.value).isEqualTo(SHOW_SAVE_FAILED_DIALOG_EVENT)
    }

    @Test
    fun shouldDeleteItemSuccessfully() {
        viewModel.uuid.set(UUID)

        whenever(databaseClientMock.deleteItem(UUID)).thenReturn(Completable.complete())

        viewModel.delete()

        verify(databaseClientMock).deleteItem(UUID)
        assert(viewModel.events.value).isEqualTo(FINISH_ACTIVITY_EVENT)
    }

    @Test
    fun shouldMarkItemAsValidWhenNameIsSet() {
        viewModel.name.set(ITEM_NAME)

        assert(viewModel.isValid()).isEqualTo(true)
    }

    @Test
    fun shouldNotMarkItemAsValidWhenNameNotIsSet() {
        assert(viewModel.isValid()).isEqualTo(false)
    }

    @Test
    fun shouldSetSelectedProjectToLastUsed() {
        whenever(
                sharedPreferencesMock.getString(LATEST_SELECTED_PROJECT_PREFERENCES_KEY, "Inbox")
        ).thenReturn(SECOND_PROJECT_NAME)

        viewModel = ItemViewModel(applicationMock, databaseClientMock, sharedPreferencesMock)

        assert(viewModel.projectName).valueIsEqualTo(SECOND_PROJECT_NAME)
    }
}