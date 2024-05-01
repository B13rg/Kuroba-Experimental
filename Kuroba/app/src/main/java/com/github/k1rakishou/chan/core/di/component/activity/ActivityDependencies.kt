package com.github.k1rakishou.chan.core.di.component.activity

import com.github.k1rakishou.chan.core.di.module.activity.ActivityScopedViewModelFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager

interface ActivityDependencies {
  val globalWindowInsetsManager: GlobalWindowInsetsManager
  val injectedViewModelFactory: ActivityScopedViewModelFactory
}