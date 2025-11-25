package aaa.fucklocation.ui.util

import android.widget.Toast
import androidx.annotation.StringRes
import aaa.fucklocation.hmaApp

fun makeToast(@StringRes resId: Int) {
    Toast.makeText(hmaApp, resId, Toast.LENGTH_SHORT).show()
}
