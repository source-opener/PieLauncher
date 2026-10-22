package de.markusfisch.android.pielauncher.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

import de.markusfisch.android.pielauncher.preference.Preferences;

// Android copies app data to the Google account when the phone is set to
// back up. The launcher's data tells which apps someone has and uses, so
// it stays out of that backup unless the setting allows it. Moving to a
// new phone by cable or local transfer is not a cloud backup and still
// works. Runs in a restricted process, so it must not rely on
// PieLauncherApp having been set up.
public class BackupGate extends BackupAgent {
	@Override
	public void onBackup(
			ParcelFileDescriptor oldState,
			BackupDataOutput data,
			ParcelFileDescriptor newState) {
	}

	@Override
	public void onRestore(
			BackupDataInput data,
			int appVersionCode,
			ParcelFileDescriptor newState) {
	}

	@SuppressLint("UseRequiresApi")
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onFullBackup(FullBackupDataOutput data) throws IOException {
		if (Preferences.allowsCloudBackup(this) || isDeviceTransfer(data)) {
			super.onFullBackup(data);
		}
	}

	@SuppressLint("UseRequiresApi")
	@TargetApi(Build.VERSION_CODES.P)
	private static boolean isDeviceTransfer(FullBackupDataOutput data) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
				(data.getTransportFlags() &
						FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0;
	}
}
