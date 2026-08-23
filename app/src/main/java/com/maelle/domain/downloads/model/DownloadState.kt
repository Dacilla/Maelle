package com.maelle.domain.downloads.model

enum class DownloadState {
    Queued,
    Preparing,
    WaitingForServer,
    Downloading,
    Paused,
    Completed,
    Failed,
    NeedsReconciliation,
}
