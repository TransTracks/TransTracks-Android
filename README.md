# TransTracks-Android

> **This project is archived and no longer maintained.** TransTracks has been retired from the Google Play Store, and the source here is no longer being updated. Issues and pull requests are disabled. If you had the app installed and want to recover your data, see [Recovering your data](#recovering-your-data-if-the-app-was-on-your-phone) below.

TransTracks was a transition tracking application made for transgender people, focused on photo-based progress tracking.

## Project status

No new features, no bug fixes, no pull requests being reviewed, no issues being triaged. The code is preserved here for reference and so former users can recover their data. For data recovery, see the section below.

## Recovering your data if the app was on your phone

TransTracks has been retired from the Google Play Store. Unlike many retired apps, the Android version shipped with a built-in Export feature that bundles all your entries and photos into a single `.ttbackup` file. If you still have the app installed, or you can reinstall it, you can get everything out yourself.

### If the app is still installed on your phone

This is the simple path.

1. Open TransTracks.
2. Go to **Settings**.
3. Tap **Export**. The app will bundle your data into a `.ttbackup` file and open Android's share sheet.
4. Share or save that file somewhere safe: email it to yourself, upload it to Google Drive, save it to your Files app, whatever works best.

The `.ttbackup` is just a zip file with a custom extension. If you rename it to `.zip`, you can open it on any computer to see the raw photos and the exported data inside.

### If you uninstalled the app but still use the same Google account on your phone

Apps that have been removed from the Play Store are still available for reinstall by people who previously installed them. Open the Play Store, go to **Manage apps and device → Manage → Not installed**, find TransTracks, and tap Install. If Android's auto-backup was enabled on your phone (it usually is by default), your data should restore along with the app. Then follow the steps above to export it.

If the data doesn't come back after reinstalling, the auto-backup either wasn't enabled or has expired. Google automatically deletes an app's cloud backup after roughly two months of not using the device, so if the phone sat unused for a while, the backup may be gone.

### If the app is gone and the phone has been wiped

Unfortunately, this is the case with no realistic recovery path. Your data lived in the app's private internal storage, which Android's sandbox protects from other apps and which is erased when the app is uninstalled. Third-party "recovery" tools that claim to extract protected app data without root access do not actually work for this use case, so please don't spend money on them.

### If you had TransTracks on iOS as well

See the [TransTracks-iOS repo](https://github.com/TransTracks/TransTracks-iOS) for its recovery instructions. The iOS app stored its data differently and the steps are not the same.

I'm unfortunately not able to do one-on-one troubleshooting on this, so I really hope the steps above get you there. Good luck!

## License

```
Copyright (C) 2018 - 2021 TransTracks

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
