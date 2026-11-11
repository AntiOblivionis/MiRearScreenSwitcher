package com.tgwgroup.MiRearScreenSwitcher;

oneway interface IRecordingObserver {
    void onRecordingStopped(String path, boolean saved);
}
