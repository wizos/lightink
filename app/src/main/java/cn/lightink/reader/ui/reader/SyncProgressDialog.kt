package cn.lightink.reader.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.model.BookSyncProgress
import cn.lightink.reader.module.INTENT_PROGRESS
import kotlinx.android.synthetic.main.dialog_sync_progress.view.*
import java.text.SimpleDateFormat
import java.util.*

class SyncProgressDialog : DialogFragment() {

    private val controller by lazy { ViewModelProvider(activity!!)[ReaderController::class.java] }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_sync_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val progress = arguments?.getParcelable<BookSyncProgress>(INTENT_PROGRESS)
        view.mSyncProgressDate.text = progress?.run { SimpleDateFormat("yyyy / M / d", Locale.CHINESE).format(startAt) }
        view.mSyncProgressChapter.text = progress?.title
        view.mSyncProgressButton.setOnClickListener {
            progress?.run { controller.syncProgress(this) }
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(resources.displayMetrics.widthPixels - resources.getDimensionPixelSize(R.dimen.dimen1x) * 2, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setDimAmount(0.4F)
    }

    fun show(manager: FragmentManager) {
        show(manager, INTENT_PROGRESS)
    }

    companion object {
        fun newInstance(progress: BookSyncProgress) = SyncProgressDialog().apply {
            arguments = Bundle().apply { putParcelable(INTENT_PROGRESS, progress) }
        }
    }

}