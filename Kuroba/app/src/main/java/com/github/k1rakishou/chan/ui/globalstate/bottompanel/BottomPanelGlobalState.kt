package com.github.k1rakishou.chan.ui.globalstate.bottompanel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IBottomPanelGlobalState {
  interface Readable {
    val controllersHoldingBottomPanel: StateFlow<Set<ControllerKey>>
    val bottomPanelHeight: StateFlow<Dp>
  }

  interface Writeable {
    fun onBottomPanelHeightKnown(height: Dp)
    fun onBottomPanelShown(controllerKey: ControllerKey)
    fun onBottomPanelHidden(controllerKey: ControllerKey)
  }
}

class BottomPanelGlobalState : IBottomPanelGlobalState.Readable, IBottomPanelGlobalState.Writeable {
  private val _controllersHoldingBottomPanel = MutableStateFlow<Set<ControllerKey>>(emptySet())
  override val controllersHoldingBottomPanel: StateFlow<Set<ControllerKey>>
    get() = _controllersHoldingBottomPanel.asStateFlow()

  private val _bottomPanelHeight = MutableStateFlow<Dp>(0.dp)
  override val bottomPanelHeight: StateFlow<Dp>
    get() = _bottomPanelHeight.asStateFlow()

  override fun onBottomPanelHeightKnown(height: Dp) {
    _bottomPanelHeight.value = height
  }

  override fun onBottomPanelShown(controllerKey: ControllerKey) {
    _controllersHoldingBottomPanel.value += controllerKey
  }

  override fun onBottomPanelHidden(controllerKey: ControllerKey) {
    _controllersHoldingBottomPanel.value -= controllerKey

    if (_controllersHoldingBottomPanel.value.isEmpty()) {
      _bottomPanelHeight.value = 0.dp
    }
  }

}