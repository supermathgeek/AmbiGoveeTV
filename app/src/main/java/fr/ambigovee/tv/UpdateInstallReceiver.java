package fr.ambigovee.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

/**
 * Reçoit le résultat du PackageInstaller.
 *
 * Important :
 * - succès : nettoie le fichier pending ;
 * - échec : libère le verrou d'installation pour permettre un nouvel essai ;
 * - action utilisateur : garde le verrou jusqu'au résultat final.
 */
public class UpdateInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm;

            if (Build.VERSION.SDK_INT >= 33) {
                confirm = intent.getParcelableExtra(
                        Intent.EXTRA_INTENT,
                        Intent.class
                );
            } else {
                confirm = intent.getParcelableExtra(
                        Intent.EXTRA_INTENT
                );
            }

            if (confirm != null) {
                confirm.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );
                context.startActivity(confirm);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdateManager.markInstallFinished(
                    context,
                    true
            );

            Toast.makeText(
                    context,
                    "AmbiGovee mis à jour ✓",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        UpdateManager.markInstallFinished(
                context,
                false
        );

        String message = intent.getStringExtra(
                PackageInstaller.EXTRA_STATUS_MESSAGE
        );

        Toast.makeText(
                context,
                "Échec de la mise à jour"
                        + (message == null
                        ? ""
                        : " : " + message),
                Toast.LENGTH_LONG
        ).show();
    }
}
