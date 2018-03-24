package com.kumar.shushil.filemanager.dialogs

import android.support.v7.app.AlertDialog
import android.view.View
import android.view.WindowManager
import com.kumar.shushil.filemanager.R
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import kotlinx.android.synthetic.main.dialog_create_new.view.*
import java.io.File
import java.io.IOException

class CreateNewItemDialog(val activity: BaseSimpleActivity, val path: String, val callback: (success: Boolean) -> Unit) {
    private val view = activity.layoutInflater.inflate(R.layout.dialog_create_new, null)

    init {
        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            activity.setupDialogStuff(view, this, R.string.create_new) {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                    val name = view.item_name.value
                    if (name.isEmpty()) {
                        activity.toast(R.string.empty_name)
                    } else if (name.isAValidFilename()) {
                        val newPath = "$path/$name"
                        if (activity.getDoesFilePathExist(newPath)) {
                            activity.toast(R.string.name_taken)
                            return@OnClickListener
                        }

                        if (view.dialog_radio_group.checkedRadioButtonId == R.id.dialog_radio_directory) {
                            createDirectory(newPath, this) {
                                callback(it)
                            }
                        } else {
                            createFile(newPath, this) {
                                callback(it)
                            }
                        }
                    } else {
                        activity.toast(R.string.invalid_name)
                    }
                })
            }
        }
    }

    private fun createDirectory(path: String, alertDialog: AlertDialog, callback: (Boolean) -> Unit) {
        when {
            activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
                val documentFile = activity.getDocumentFile(path.getParentPath())
                if (documentFile == null) {
                    val error = String.format(activity.getString(R.string.could_not_create_folder), path)
                    activity.showErrorToast(error)
                    callback(false)
                    return@handleSAFDialog
                }
                documentFile.createDirectory(path.getFilenameFromPath())
                success(alertDialog)
            }
            File(path).mkdirs() -> {
                success(alertDialog)
            }
            else -> callback(false)
        }
    }

    private fun createFile(path: String, alertDialog: AlertDialog, callback: (Boolean) -> Unit) {
        try {
            if (activity.needsStupidWritePermissions(path)) {
                activity.handleSAFDialog(path) {
                    val documentFile = activity.getDocumentFile(path.getParentPath())
                    if (documentFile == null) {
                        val error = String.format(activity.getString(R.string.could_not_create_file), path)
                        activity.showErrorToast(error)
                        callback(false)
                        return@handleSAFDialog
                    }
                    documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())
                    success(alertDialog)
                }
            } else if (File(path).createNewFile()) {
                success(alertDialog)
            }
        } catch (exception: IOException) {
            activity.showErrorToast(exception)
            callback(false)
        }
    }

    private fun success(alertDialog: AlertDialog) {
        alertDialog.dismiss()
        callback(true)
    }
}
