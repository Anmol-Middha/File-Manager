package com.kumar.shushil.filemanager.fragments

import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kumar.shushil.filemanager.R
import com.kumar.shushil.filemanager.activities.MainActivity
import com.kumar.shushil.filemanager.activities.SimpleActivity
import com.kumar.shushil.filemanager.adapters.ItemsAdapter
import com.kumar.shushil.filemanager.dialogs.CreateNewItemDialog
import com.kumar.shushil.filemanager.extensions.config
import com.kumar.shushil.filemanager.extensions.isPathOnRoot
import com.kumar.shushil.filemanager.extensions.tryOpenPathIntent
import com.kumar.shushil.filemanager.helpers.PATH
import com.kumar.shushil.filemanager.helpers.RootHelpers
import com.kumar.shushil.filemanager.interfaces.ItemOperationsListener
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.StoragePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.OTG_PATH
import com.simplemobiletools.commons.helpers.SORT_BY_SIZE
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.Breadcrumbs
import com.stericson.RootTools.RootTools
import kotlinx.android.synthetic.main.items_fragment.*
import kotlinx.android.synthetic.main.items_fragment.view.*
import java.io.File
import java.util.HashMap
import kotlin.collections.ArrayList

class ItemsFragment : Fragment(), ItemOperationsListener, Breadcrumbs.BreadcrumbsListener {
    var currentPath = ""
    var isGetContentIntent = false
    var isGetRingtonePicker = false
    var isPickMultipleIntent = false
    var isFirstResume = true
    var storedItems = ArrayList<FileDirItem>()

    private var showHidden = false
    private var skipItemUpdating = false
    private var scrollStates = HashMap<String, Parcelable>()

    private var storedTextColor = 0

    private lateinit var mView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mView = inflater.inflate(R.layout.items_fragment, container, false)!!
        storeStateVariables()
        return mView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView.apply {
            items_swipe_refresh.setOnRefreshListener { refreshItems() }
            items_fab.setOnClickListener { createNewItem() }
            breadcrumbs.listener = this@ItemsFragment
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PATH, currentPath)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            currentPath = savedInstanceState.getString(PATH)
            storedItems.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        context!!.updateTextColors(mView as ViewGroup)
        mView.items_fastscroller.updatePrimaryColor()
        val newTextColor = context!!.config.textColor
        if (storedTextColor != newTextColor) {
            storedItems = ArrayList()
            (items_list.adapter as? ItemsAdapter)?.apply {
                updateTextColor(newTextColor)
                initDrawables()
            }
            mView.breadcrumbs.updateColor(newTextColor)
            storedTextColor = newTextColor
        }

        items_fastscroller.updateBubbleColors()
        items_fastscroller.allowBubbleDisplay = context!!.config.showInfoBubble
        if (!isFirstResume) {
            refreshItems()
        }
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    private fun storeStateVariables() {
        context!!.config.apply {
            storedTextColor = textColor
        }
    }

    fun openPath(path: String, forceRefresh: Boolean = false) {
        if (!isAdded || (activity as? BaseSimpleActivity)?.isAskingPermissions == true) {
            return
        }

        var realPath = if (path == OTG_PATH) OTG_PATH else path.trimEnd('/')
        if (realPath.isEmpty()) {
            realPath = "/"
        }

        scrollStates[currentPath] = getScrollState()
        currentPath = realPath
        showHidden = context!!.config.shouldShowHidden
        getItems(currentPath) { originalPath, fileDirItems ->
            if (currentPath != originalPath || !isAdded) {
                return@getItems
            }

            FileDirItem.sorting = context!!.config.getFolderSorting(currentPath)
            fileDirItems.sort()
            activity!!.runOnUiThread {
                addItems(fileDirItems, forceRefresh)
            }
        }
    }

    private fun addItems(items: ArrayList<FileDirItem>, forceRefresh: Boolean = false) {
        skipItemUpdating = false
        mView.apply {
            activity?.runOnUiThread {
                items_swipe_refresh?.isRefreshing = false
                if (!forceRefresh && items.hashCode() == storedItems.hashCode()) {
                    return@runOnUiThread
                }

                mView.breadcrumbs.setBreadcrumb(currentPath)
                storedItems = items
                ItemsAdapter(activity as SimpleActivity, storedItems, this@ItemsFragment, items_list, isPickMultipleIntent, items_fastscroller) {
                    itemClicked(it as FileDirItem)
                }.apply {
                    setupDragListener(true)
                    addVerticalDividers(true)
                    items_list.adapter = this
                }
                items_fastscroller.allowBubbleDisplay = context.config.showInfoBubble
                items_fastscroller.setViews(items_list, items_swipe_refresh) {
                    items_fastscroller.updateBubbleText(storedItems.getOrNull(it)?.getBubbleText() ?: "")
                }

                getRecyclerLayoutManager().onRestoreInstanceState(scrollStates[currentPath])
                items_list.onGlobalLayout {
                    items_fastscroller.setScrollTo(items_list.computeVerticalScrollOffset())
                }
            }
        }
    }

    fun getScrollState() = getRecyclerLayoutManager().onSaveInstanceState()

    private fun getRecyclerLayoutManager() = (mView.items_list.layoutManager as LinearLayoutManager)

    private fun getItems(path: String, callback: (originalPath: String, items: ArrayList<FileDirItem>) -> Unit) {
        skipItemUpdating = false
        Thread {
            if (activity?.isActivityDestroyed() == false) {
                if (path.startsWith(OTG_PATH)) {
                    val getProperFileSize = context!!.config.sorting and SORT_BY_SIZE != 0
                    context!!.getOTGItems(path, context!!.config.shouldShowHidden, getProperFileSize) {
                        callback(path, it)
                    }
                } else if (!context!!.config.enableRootAccess || !context!!.isPathOnRoot(path)) {
                    getRegularItemsOf(path, callback)
                } else {
                    RootHelpers().getFiles(activity as SimpleActivity, path, callback)
                }
            }
        }.start()
    }

    private fun getRegularItemsOf(path: String, callback: (originalPath: String, items: ArrayList<FileDirItem>) -> Unit) {
        val items = ArrayList<FileDirItem>()
        val files = File(path).listFiles()?.filterNotNull()
        if (files != null) {
            for (file in files) {
                val curPath = file.absolutePath
                val curName = curPath.getFilenameFromPath()
                if (!showHidden && curName.startsWith(".")) {
                    continue
                }

                val children = file.getDirectChildrenCount(showHidden)
                val size = if (file.isDirectory && context?.config?.sorting == SORT_BY_SIZE) file.getProperSize(showHidden) else file.length()
                val fileDirItem = FileDirItem(curPath, curName, file.isDirectory, children, size)
                items.add(fileDirItem)
            }
        }
        callback(path, items)
    }

    private fun itemClicked(item: FileDirItem) {
        if (item.isDirectory) {
            (activity as? MainActivity)?.apply {
                skipItemUpdating = isSearchOpen
                openedDirectory()
            }
            openPath(item.path)
        } else {
            val path = item.path
            if (isGetContentIntent) {
                (activity as MainActivity).pickedPath(path)
            } else if (isGetRingtonePicker) {
                if (path.isAudioFast()) {
                    (activity as MainActivity).pickedRingtone(path)
                } else {
                    activity?.toast(R.string.select_audio_file)
                }
            } else {
                activity!!.tryOpenPathIntent(path, false)
            }
        }
    }

    fun searchQueryChanged(text: String) {
        Thread {
            val filtered = storedItems.filter { it.name.contains(text, true) } as ArrayList
            filtered.sortBy { !it.name.startsWith(text, true) }
            activity?.runOnUiThread {
                (items_list.adapter as? ItemsAdapter)?.updateItems(filtered)
            }
        }.start()
    }

    fun searchClosed() {
        if (!skipItemUpdating) {
            (items_list.adapter as? ItemsAdapter)?.updateItems(storedItems)
        }
        skipItemUpdating = false
    }

    private fun createNewItem() {
        CreateNewItemDialog(activity as SimpleActivity, currentPath) {
            if (it) {
                refreshItems()
            } else {
                activity?.toast(R.string.unknown_error_occurred)
            }
        }
    }

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(activity as SimpleActivity, currentPath) {
                openPath(it)
            }
        } else {
            val item = breadcrumbs.getChildAt(id).tag as FileDirItem
            openPath(item.path)
        }
    }

    override fun refreshItems() {
        openPath(currentPath)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        val hasFolder = files.any { it.isDirectory }
        if (context!!.isPathOnRoot(files.firstOrNull()?.path ?: context!!.config.internalStoragePath)) {
            files.forEach {
                RootTools.deleteFileOrDirectory(it.path, false)
            }
        } else {
            (activity as SimpleActivity).deleteFiles(files, hasFolder) {
                if (!it) {
                    activity!!.runOnUiThread {
                        activity!!.toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }
}
