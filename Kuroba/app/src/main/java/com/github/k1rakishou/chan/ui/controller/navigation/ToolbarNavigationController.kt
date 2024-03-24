package com.github.k1rakishou.chan.ui.controller.navigation

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.controller.transition.ControllerTransition
import com.github.k1rakishou.chan.features.toolbar_v2.KurobaToolbarTransition

abstract class ToolbarNavigationController(context: Context) : NavigationController(context) {

  override fun transition(
    from: Controller?,
    to: Controller?,
    pushing: Boolean,
    controllerTransition: ControllerTransition?
  ) {
    if (from == null && to == null) {
      return
    }

    super.transition(from, to, pushing, controllerTransition)

    if (to != null) {
      requireToolbarNavController().toolbarState.showToolbar()
    }
  }

  override fun beginSwipeTransition(from: Controller?, to: Controller?): Boolean {
    if (from == null && to == null) {
      return false
    }

    if (!super.beginSwipeTransition(from, to)) {
      return false
    }

    requireToolbarNavController().toolbarState.showToolbar()

    if (to != null) {
      requireToolbarNavController().toolbarState.onTransitionProgressStart(
        other = to.toolbarState,
        transitionMode = KurobaToolbarTransition.TransitionMode.Out
      )
    }

    return true
  }

  override fun swipeTransitionProgress(progress: Float) {
    super.swipeTransitionProgress(progress)

    requireToolbarNavController().toolbarState.onTransitionProgress(progress)
  }

  override fun endSwipeTransition(from: Controller?, to: Controller?, finish: Boolean) {
    if (from == null && to == null) {
      return
    }

    super.endSwipeTransition(from, to, finish)

    requireToolbarNavController().toolbarState.showToolbar()

    if (finish && to != null) {
      requireToolbarNavController().toolbarState.onTransitionProgressFinished(to.toolbarState)
    } else if (!finish && from != null) {
      requireToolbarNavController().toolbarState.onTransitionProgressFinished(from.toolbarState)
    }
  }

  override fun onBack(): Boolean {
    return requireToolbarNavController().toolbarState.onBack() || super.onBack()
  }

}
