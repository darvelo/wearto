package se.yverling.wearto.items.edit

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.content.Context
import android.content.SharedPreferences
import android.databinding.BindingAdapter
import android.databinding.InverseBindingAdapter
import android.databinding.InverseBindingListener
import android.databinding.Observable
import android.databinding.ObservableField
import android.support.annotation.StringRes
import android.support.design.widget.TextInputLayout
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import se.yverling.wearto.R
import se.yverling.wearto.core.SingleLiveEvent
import se.yverling.wearto.core.WearToApplication
import se.yverling.wearto.core.db.DatabaseClient
import se.yverling.wearto.core.entities.Project
import se.yverling.wearto.items.edit.ItemViewModel.Events.*
import javax.inject.Inject

internal const val LATEST_SELECTED_PROJECT_PREFERENCES_KEY = "LATEST_SELECTED_PROJECT"

class ItemViewModel @Inject constructor(
        app: Application,
        private val databaseClient: DatabaseClient,
        private val sharedPreferences: SharedPreferences
) : AndroidViewModel(app), AnkoLogger {

    val uuid = ObservableField<String>()
    val name = ObservableField<String>()
    val projectName = ObservableField<String>()
    val itemErrorMessage = ObservableField<String>("")
    internal val events = SingleLiveEvent<Events>()

    private val disposables = CompositeDisposable()

    private val adapter = ArrayAdapter<String>(getApplication() as Context, R.layout.spinner_list_header, arrayListOf())

    init {
        adapter.setDropDownViewResource(R.layout.spinner_list_item)

        val disposable = databaseClient.findAllProjects()
                .doOnSuccess {
                    adapter.addAll(getSpinnerArray(it))
                }
                .flatMapMaybe {
                    Maybe.fromCallable<String> {
                        val selectedProjectName
                                = sharedPreferences.getString(LATEST_SELECTED_PROJECT_PREFERENCES_KEY, "Inbox")

                        if (it.find { it.name == selectedProjectName } != null) {
                            selectedProjectName
                        } else {
                            null
                        }
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onSuccess = {
                            projectName.set(it)
                        },

                        onError = {
                            error(it)
                        }
                )
        disposables.add(disposable)

        name.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                if (!name.get().isNullOrBlank()) {
                    itemErrorMessage.set("")
                }
            }
        })
    }

    override fun onCleared() {
        disposables.clear()
    }

    fun edit(uuid: String) {
        val disposable = databaseClient.findItemWithProjectByUuid(uuid)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onSuccess = {
                            this.uuid.set(it.item.uuid)
                            name.set(it.item.name)
                            projectName.set(it.project.name)
                        },

                        onError = {
                            error(it)
                        }
                )
        disposables.add(disposable)
    }

    fun save() {
        if (uuid.get() != null) {
            val disposable = databaseClient.updateItem(uuid.get()!!, name.get()!!, projectName.get()!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                            onComplete = {
                                events.value = FINISH_ACTIVITY_EVENT
                            },

                            onError = {
                                events.value = SHOW_SAVE_FAILED_DIALOG_EVENT
                            }
                    )
            disposables.add(disposable)
        } else {
            val disposable = databaseClient.saveItem(name.get()!!, projectName.get()!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                            onComplete = {
                                events.value = FINISH_ACTIVITY_EVENT
                            },

                            onError = {
                                events.value = SHOW_SAVE_FAILED_DIALOG_EVENT
                            }
                    )
            disposables.add(disposable)
        }
        saveLastSelectedProject()
    }

    fun delete() {
        val disposable = databaseClient.deleteItem(uuid.get()!!)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onComplete = {
                            events.value = FINISH_ACTIVITY_EVENT
                        },

                        onError = {
                            error(it)
                        }
                )
        disposables.add(disposable)
    }

    fun isValid(): Boolean {
        return name.get()?.isNotBlank() ?: false
    }

    fun showValidationMessage(message: String) {
        itemErrorMessage.set(message)
    }

    fun editorActionListener(): TextView.OnEditorActionListener {
        return TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (!isValid()) {
                    showValidationMessage(getApplication<WearToApplication>().getString(R.string.item_name_is_required_label))
                    events.value = HIDE_KEYBOARD_EVENT
                } else {
                    save()
                }
            }
            false
        }
    }

    fun arrayAdapter(): ArrayAdapter<String> = adapter

    private fun getSpinnerArray(projects: List<Project>): ArrayList<String> {
        val names = projects.filter { it.name != "Inbox" }.map { it.name }
        return ArrayList(listOf("Inbox") + names)
    }

    private fun saveLastSelectedProject() {
        val disposable = Completable.fromRunnable {
            val editor = sharedPreferences.edit()
            editor.putString(LATEST_SELECTED_PROJECT_PREFERENCES_KEY, projectName.get()!!)
            editor.apply()
        }
                .subscribeBy(
                        onError = {
                            error(it)
                        }
                )
        disposables.add(disposable)
    }

    enum class Events {
        FINISH_ACTIVITY_EVENT,
        HIDE_KEYBOARD_EVENT,
        SHOW_SAVE_FAILED_DIALOG_EVENT
    }
}

@BindingAdapter("errorMessage")
fun setErrorMessage(textInputLayout: TextInputLayout, errorMessage: String) {
    textInputLayout.error = errorMessage
}

@BindingAdapter("hint")
fun setHint(editText: EditText, @StringRes resourceId: Int) {
    editText.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            editText.setHint(resourceId)
        } else {
            editText.hint = ""
        }
    }
}

@BindingAdapter("editorActionListener")
fun setEditorActionListener(editText: EditText, listener: TextView.OnEditorActionListener) {
    editText.setOnEditorActionListener(listener)
}

@BindingAdapter("spinnerAdapter")
fun setAdapter(view: Spinner, adapter: SpinnerAdapter) {
    view.adapter = adapter
}

@BindingAdapter(value = ["selectedValue", "selectedValueAttrChanged"], requireAll = false)
fun bindSpinnerData(spinner: Spinner, newSelectedValue: String?, newTextAttrChanged: InverseBindingListener) {
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
            newTextAttrChanged.onChange()
        }

        override fun onNothingSelected(parent: AdapterView<*>) {}
    }

    if (newSelectedValue != null) {
        val position = (spinner.adapter as ArrayAdapter<String>).getPosition(newSelectedValue)
        spinner.setSelection(position, true)
    }
}

@InverseBindingAdapter(attribute = "selectedValue", event = "selectedValueAttrChanged")
fun captureSelectedValue(spinner: Spinner): String {
    return spinner.selectedItem as String
}

