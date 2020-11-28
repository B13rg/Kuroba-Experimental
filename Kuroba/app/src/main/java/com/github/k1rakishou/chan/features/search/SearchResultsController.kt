package com.github.k1rakishou.chan.features.search

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent
import com.github.k1rakishou.chan.core.site.sites.search.PageCursor
import com.github.k1rakishou.chan.core.usecase.GlobalSearchUseCase
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerState
import com.github.k1rakishou.chan.features.search.data.SearchResultsControllerStateData
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchPostDividerView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchEndOfResultsView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchErrorView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchLoadingView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchPostDividerView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchPostGapView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchPostView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import javax.inject.Inject

class SearchResultsController(
  context: Context,
  private val siteDescriptor: SiteDescriptor,
  private val query: String
) : Controller(context), SearchResultsView {

  @Inject
  lateinit var globalSearchUseCase: GlobalSearchUseCase
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val presenter by lazy {
    SearchResultsPresenter(
      siteDescriptor = siteDescriptor,
      query = query,
      globalSearchUseCase = globalSearchUseCase,
      themeEngine = themeEngine
    )
  }

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  override fun injectDependencies(component: StartActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    updateTitle(null)
    navigation.swipeable = false

    view = inflate(context, R.layout.controller_search_results)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.swapAdapter(null, true)
    presenter.onDestroy()
  }

  override fun onBack(): Boolean {
    presenter.resetSavedState()
    presenter.resetLastRecyclerViewScrollState()
    return super.onBack()
  }

  private fun onStateChanged(state: SearchResultsControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        SearchResultsControllerState.Uninitialized -> {
          // no-op
        }
        is SearchResultsControllerState.NothingFound -> {
          epoxyTextView {
            id("search_result_controller_empty_view")
            message("Nothing was found by query \"${state.query}\"")
          }
        }
        is SearchResultsControllerState.Data -> renderDataState(state.data)
      }
    }
  }

  private fun EpoxyController.renderDataState(data: SearchResultsControllerStateData) {
    addOneshotModelBuildListener { tryRestorePreviousPosition() }

    data.searchPostInfoList.forEachIndexed { index, searchPostInfo ->
      if (index != 0 && searchPostInfo.opInfo != null) {
        epoxySearchPostGapView {
          id("epoxy_search_post_gap_view_${searchPostInfo.postDescriptor.serializeToString()}")
        }
      }

      epoxySearchPostView {
        id("epoxy_search_post_view_${searchPostInfo.postDescriptor.serializeToString()}}")
        postDescriptor(searchPostInfo.postDescriptor)
        postOpInfo(searchPostInfo.opInfo?.spannedText)
        postInfo(searchPostInfo.postInfo.spannedText)
        thumbnail(searchPostInfo.thumbnail)
        postComment(searchPostInfo.postComment.spannedText)
        onBind { _, _, _ ->
          presenter.updateLastRecyclerViewScrollState(RecyclerUtils.getIndexAndTop(epoxyRecyclerView))
        }
        onPostClickListener { postDescriptor ->
          presenter.updateLastRecyclerViewScrollState(RecyclerUtils.getIndexAndTop(epoxyRecyclerView))
          onSearchPostClicked(postDescriptor)
        }
      }

      val isNextPostOP = data.searchPostInfoList.getOrNull(index + 1)?.opInfo != null
      if (!isNextPostOP) {
        epoxySearchPostDividerView {
          id("epoxy_divider_view_$index")
          updateMargins(NEW_MARGINS)
        }
      }
    }

    if (data.errorInfo != null) {
      epoxySearchErrorView {
        id("epoxy_search_error_view")
        errorText(data.errorInfo.errorText)
        clickListener { presenter.reloadCurrentPage() }
      }

      return
    }

    val nextPageCursor = data.nextPageCursor
    if (nextPageCursor !is PageCursor.End) {
      val searchLoadingViewId = if (nextPageCursor is PageCursor.Empty) {
        "epoxy_search_loading_view_initial"
      } else {
        val page = nextPageCursor as PageCursor.Page
        "epoxy_search_loading_view_${page.value}"
      }

      epoxySearchLoadingView {
        id(searchLoadingViewId)
        onBind { _, _, _ -> presenter.loadNewPage(data) }
      }
    } else {
      epoxySearchEndOfResultsView {
        id("epoxy_search_end_of_results_view")
        text(getString(R.string.controller_search_results_end_of_list))
      }
    }

    updateTitle(data.currentQueryInfo?.totalFoundEntries)
  }

  private fun tryRestorePreviousPosition() {
    val indexAndTop = presenter.lastRecyclerViewScrollStateOrNull()
      ?: return

    (epoxyRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
      indexAndTop.index,
      indexAndTop.top
    )
  }

  private fun onSearchPostClicked(postDescriptor: PostDescriptor) {
    (context as? StartActivity)?.loadThread(postDescriptor)
  }

  private fun updateTitle(totalFound: Int?) {
    if (totalFound == null) {
      navigation.title = getString(
        R.string.controller_search_searching,
        siteDescriptor.siteName,
        query
      )
    } else {
      navigation.title = getString(
        R.string.controller_search_results,
        siteDescriptor.siteName,
        query,
        totalFound
      )
    }

    requireNavController().requireToolbar().updateTitle(navigation)
  }

  companion object {
    private val DIVIDER_VERTICAL_MARGINS = dp(8f)
    private val DIVIDER_HORIZONTAL_MARGINS = dp(4f)

    private val NEW_MARGINS = EpoxySearchPostDividerView.NewMargins(
      top = DIVIDER_VERTICAL_MARGINS,
      bottom = DIVIDER_VERTICAL_MARGINS,
      left = DIVIDER_HORIZONTAL_MARGINS,
      right = DIVIDER_HORIZONTAL_MARGINS
    )
  }
}