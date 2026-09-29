package hu.rsc.shelflife.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import hu.rsc.shelflife.R

/**
 * Visszajelzes kuldese e-mailben: a targy es a torzs elore kitoltve az app
 * verziojaval es a telefon adataival (a beta-tesztelok hibajelentesehez).
 * A cimzett: R.string.feedback_email (ha ures, a levelezo app kerdezi meg).
 */
fun sendFeedback(context: Context) {
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    val subject = context.getString(R.string.feedback_subject, version)
    val body = context.getString(
        R.string.feedback_body,
        version,
        "${Build.MANUFACTURER} ${Build.MODEL}",
        Build.VERSION.RELEASE
    )
    val address = context.getString(R.string.feedback_email).trim()
    val uri = Uri.parse(
        "mailto:" + Uri.encode(address) +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.feedback_no_email_app, Toast.LENGTH_LONG).show()
    }
}
