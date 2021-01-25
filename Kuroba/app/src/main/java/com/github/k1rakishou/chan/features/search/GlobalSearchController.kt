package com.github.k1rakishou.chan.features.search

import android.content.Context
import android.view.View
import com.airbnb.epoxy.EpoxyController
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerState
import com.github.k1rakishou.chan.features.search.data.GlobalSearchControllerStateData
import com.github.k1rakishou.chan.features.search.data.SearchParameters
import com.github.k1rakishou.chan.features.search.data.SitesWithSearch
import com.github.k1rakishou.chan.features.search.epoxy.epoxyBoardSelectionButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchButtonView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchInputView
import com.github.k1rakishou.chan.features.search.epoxy.epoxySearchSiteView
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import java.lang.ref.WeakReference
import javax.inject.Inject

class GlobalSearchController(context: Context)
  : Controller(context),
  GlobalSearchView {

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val presenter by lazy {
    GlobalSearchPresenter(siteManager)
  }

  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView

  private val inputViewRefSet = mutableListOf<WeakReference<View>>()
  private var resetSearchParameters = false

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = getString(R.string.controller_search)
    navigation.swipeable = false

    view = inflate(context, R.layout.controller_global_search)
    epoxyRecyclerView = view.findViewById(R.id.epoxy_recycler_view)

    compositeDisposable += presenter.listenForStateChanges()
      .subscribe { state -> onStateChanged(state) }

    presenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.swapAdapter(null, true)
    inputViewRefSet.clear()
    presenter.onDestroy()
  }

  override fun onBack(): Boolean {
    resetSearchParameters = true
    presenter.resetSavedState()
    return super.onBack()
  }

  override fun updateResetSearchParametersFlag(reset: Boolean) {
    resetSearchParameters = reset
  }

  override fun restoreSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    hideKeyboard()

    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters),
      false
    )
  }

  override fun openSearchResultsController(
    siteDescriptor: SiteDescriptor,
    searchParameters: SearchParameters
  ) {
    hideKeyboard()

    presenter.resetSearchResultsSavedState()
    requireNavController().pushController(
      SearchResultsController(context, siteDescriptor, searchParameters)
    )
  }

  private fun onStateChanged(state: GlobalSearchControllerState) {
    epoxyRecyclerView.withModels {
      when (state) {
        GlobalSearchControllerState.Uninitialized -> {
          // no-op
        }
        GlobalSearchControllerState.Loading -> {
          epoxyLoadingView {
            id("global_search_loading_view")
          }
        }
        GlobalSearchControllerState.Empty -> {
          epoxyTextView {
            id("global_search_empty_text_view")
            message(context.getString(R.string.controller_search_empty_sites))
          }
        }
        is GlobalSearchControllerState.Error -> {
          epoxyErrorView {
            id("global_search_error_view")
            errorMessage(state.errorText)
          }
        }
        is GlobalSearchControllerState.Data -> onDataStateChanged(state.data)
      }
    }
  }

  private fun EpoxyController.onDataStateChanged(dataState: GlobalSearchControllerStateData) {
    epoxySearchSiteView {
      id("global_search_epoxy_site")
      bindSiteName(dataState.sitesWithSearch.selectedSite.siteDescriptor.siteName)
      bindIcon(dataState.sitesWithSearch.selectedSite.siteIconUrl)
      bindClickCallback {
        val controller = SelectSiteForSearchController(
          context = context,
          selectedSite = dataState.sitesWithSearch.selectedSite.siteDescriptor,
          onSiteSelected = { selectedSiteDescriptor -> presenter.onSearchSiteSelected(selectedSiteDescriptor) }
        )

        requireNavController().presentController(controller)
      }
    }

    val selectedSite = dataState.sitesWithSearch.selectedSite

    val canRenderSearchButton = when (selectedSite.siteGlobalSearchType) {
      SiteGlobalSearchType.SimpleQuerySearch -> renderSimpleQuerySearch(dataState)
      SiteGlobalSearchType.FuukaSearch,
      SiteGlobalSearchType.FoolFuukaSearch -> renderFoolFuukaSearch(dataState)
      SiteGlobalSearchType.SearchNotSupported -> false
    }

    if (!canRenderSearchButton) {
      return
    }

    renderSearchButton(dataState.sitesWithSearch, dataState.searchParameters)
  }

  private fun EpoxyController.renderSearchButton(
    sitesWithSearch: SitesWithSearch,
    searchParameters: SearchParameters
  ) {
    epoxySearchButtonView {
      id("global_search_button_view")
      onButtonClickListener {
        presenter.onSearchButtonClicked(sitesWithSearch.selectedSite, searchParameters)
      }
    }
  }

  private fun EpoxyController.renderFoolFuukaSearch(dataState: GlobalSearchControllerStateData): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.AdvancedSearchParameters
    val selectedSiteDescriptor = sitesWithSearch.selectedSite.siteDescriptor

    // When site selection changes with want to redraw all epoxySearchInputViews with new initialQueries
    val selectedSiteName = selectedSiteDescriptor.siteName

    var initialQuery = searchParameters.query
    var initialSubjectQuery = searchParameters.subject
    var selectedBoard = searchParameters.boardDescriptor
    var selectedBoardCode = selectedBoard?.boardCode

    if (resetSearchParameters) {
      initialQuery = ""
      initialSubjectQuery = ""
      selectedBoard = null
      selectedBoardCode = null

      resetSearchParameters = false
    }

    epoxyBoardSelectionButtonView {
      id("global_search_board_selection_button_view_$selectedSiteName")
      boardCode(selectedBoardCode)
      bindClickCallback {
        val boardsSupportingSearch = archivesManager.getBoardsSupportingSearch(selectedSiteDescriptor)
        if (boardsSupportingSearch.isEmpty()) {
          return@bindClickCallback
        }

        val controller = SelectBoardForSearchController(
          context = context,
          prevSelectedBoard = searchParameters.boardDescriptor,
          siteDescriptor = selectedSiteDescriptor,
          onBoardSelected = { boardDescriptor ->
            val updatedSearchParameters = createAdvancedSearchParamsBySite(
              siteDescriptor = selectedSiteDescriptor,
              query = initialQuery,
              subject = initialSubjectQuery,
              boardDescriptor = boardDescriptor
            )

            presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
          }
        )

        requireNavController().presentController(controller)
      }
    }

    epoxySearchInputView {
      id("global_search_fool_fuuka_search_input_comment_subject_view_$selectedSiteName")
      initialQuery(initialSubjectQuery)
      hint(context.getString(R.string.post_subject_search_query_hint))
      onTextEnteredListener { subjectQuery ->
        val updatedSearchParameters = createAdvancedSearchParamsBySite(
          siteDescriptor = selectedSiteDescriptor,
          query = initialQuery,
          subject = subjectQuery,
          boardDescriptor = selectedBoard
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    epoxySearchInputView {
      id("global_search_fool_fuuka_search_input_comment_query_view_$selectedSiteName")
      initialQuery(initialQuery)
      hint(context.getString(R.string.post_comment_search_query_hint))
      onTextEnteredListener { commentQuery ->
        val updatedSearchParameters = createAdvancedSearchParamsBySite(
          siteDescriptor = selectedSiteDescriptor,
          query = commentQuery,
          subject = initialSubjectQuery,
          boardDescriptor = selectedBoard
        )

        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    return createAdvancedSearchParamsBySite(
      siteDescriptor = selectedSiteDescriptor,
      query = initialQuery,
      subject = initialSubjectQuery,
      boardDescriptor = selectedBoard
    ).isValid()
  }

  private fun EpoxyController.renderSimpleQuerySearch(dataState: GlobalSearchControllerStateData): Boolean {
    val sitesWithSearch = dataState.sitesWithSearch
    val searchParameters = dataState.searchParameters as SearchParameters.SimpleQuerySearchParameters
    var initialQuery = searchParameters.query

    // When site selection changes with want to redraw epoxySearchInputView with new initialQuery
    val selectedSiteName = sitesWithSearch.selectedSite.siteDescriptor.siteName

    if (resetSearchParameters) {
      initialQuery = ""

      resetSearchParameters = false
    }

    epoxySearchInputView {
      id("global_search_simple_query_search_view_$selectedSiteName")
      initialQuery(initialQuery)
      hint(context.getString(R.string.post_comment_search_query_hint))
      onTextEnteredListener { query ->
        val updatedSearchParameters = SearchParameters.SimpleQuerySearchParameters(query)
        presenter.reloadWithSearchParameters(updatedSearchParameters, sitesWithSearch)
      }
      onBind { _, view, _ -> addViewToInputViewRefSet(view) }
      onUnbind { _, view -> removeViewFromInputViewRefSet(view) }
    }

    return SearchParameters.SimpleQuerySearchParameters(
      query = initialQuery
    ).isValid()
  }

  private fun createAdvancedSearchParamsBySite(
    siteDescriptor: SiteDescriptor,
    query: String,
    subject: String,
    boardDescriptor: BoardDescriptor?,
  ): SearchParameters.AdvancedSearchParameters {
    val searchType = siteManager.bySiteDescriptor(siteDescriptor)?.siteGlobalSearchType()
    checkNotNull(searchType) { "searchType is null! siteDescriptor=${siteDescriptor}" }

    when (searchType) {
      SiteGlobalSearchType.SearchNotSupported,
      SiteGlobalSearchType.SimpleQuerySearch -> {
        throw IllegalStateException("Only advanced search must be used here!")
      }
      SiteGlobalSearchType.FuukaSearch -> {
        return SearchParameters.FuukaSearchParameters(
          query = query,
          subject = subject,
          boardDescriptor = boardDescriptor
        )
      }
      SiteGlobalSearchType.FoolFuukaSearch -> {
        return SearchParameters.FoolFuukaSearchParameters(
          query = query,
          subject = subject,
          boardDescriptor = boardDescriptor
        )
      }
    }
  }

  private fun addViewToInputViewRefSet(view: View) {
    val alreadyAdded = inputViewRefSet.any { viewRef ->
      return@any viewRef.get() === view
    }

    if (alreadyAdded) {
      return
    }

    inputViewRefSet.add(WeakReference(view))
  }

  private fun removeViewFromInputViewRefSet(view: View) {
    inputViewRefSet.removeAll { viewRef -> viewRef.get() === view }
  }

  private fun hideKeyboard() {
    inputViewRefSet.forEach { viewRef ->
      viewRef.get()?.let { inputView ->
        if (inputView.hasFocus()) {
          AndroidUtils.hideKeyboard(inputView)
        }
      }
    }
  }
}