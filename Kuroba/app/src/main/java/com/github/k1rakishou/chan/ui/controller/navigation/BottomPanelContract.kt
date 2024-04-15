package com.github.k1rakishou.chan.ui.controller.navigation

import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem

interface BottomPanelContract {
  val isBottomPanelShown: Boolean
  val bottomPanelHeight: Int

  fun onBottomPanelStateChanged(func: (BottomMenuPanel.State) -> Unit)
  fun showBottomPanel(controllerKey: ControllerKey, items: List<BottomMenuPanelItem>)
  fun hideBottomPanel(controllerKey: ControllerKey)
  fun passOnBackToBottomPanel(controllerKey: ControllerKey): Boolean
}