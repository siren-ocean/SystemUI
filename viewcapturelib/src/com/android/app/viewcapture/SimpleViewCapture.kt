package com.android.app.viewcapture

import android.os.Process
import android.view.Choreographer

open class SimpleViewCapture(threadName: String) : ViewCapture(DEFAULT_MEMORY_SIZE, DEFAULT_INIT_POOL_SIZE,
    MAIN_EXECUTOR.submit { Choreographer.getInstance() }.get(),
    createAndStartNewLooperExecutor(threadName, Process.THREAD_PRIORITY_FOREGROUND))