package personal.wuqing.anonymous

import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.addTextChangedListener
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import personal.wuqing.anonymous.databinding.ActivityPostBinding
import kotlin.time.ExperimentalTime

@ExperimentalTime
class PostDetailActivity : AppCompatActivity() {
    lateinit var binding: ActivityPostBinding
    val model by viewModels<ReplyListViewModel>()

    private fun <T> LiveData<T>.observe(f: (T) -> Unit) = observe(this@PostDetailActivity, f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_post)
        binding.reply.tag = "0"
        model.post.value = (intent.getSerializableExtra("post") as Post).apply {
            showInDetail = true
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.title = "#${model.post.value!!.id}"
        binding.toolbar.setNavigationOnClickListener { finishAfterTransition() }
        val adapter = ReplyAdapter(
            replyInit = {
                root.setOnClickListener {
                    model.viewModelScope.launch {
                        binding.replyHint.hint = "回复 ${reply?.id()} (${reply?.name})"
                        binding.reply.tag = reply?.id
                        val params =
                            binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                        val behavior = params.behavior as HideBottomViewOnScrollBehavior
                        behavior.slideUp(binding.bottomBar)
                        binding.reply.requestFocus()
                        delay(100)
                        repeat(2) { // IDK Y, but repeating it really works
                            getSystemService(InputMethodManager::class.java).showSoftInput(
                                binding.reply, 0
                            )
                        }
                    }
                }
                if (reply!!.showTo()) jump.apply {
                    setOnClickListener {
                        model.viewModelScope.launch {
                            if ((binding.recycle.layoutManager as LinearLayoutManager).run {
                                    findFirstCompletelyVisibleItemPosition() > reply!!.toFloor
                                }) binding.appbar.setExpanded(true, true)
                            binding.recycle.smoothScrollToPosition(reply!!.toFloor)
                            while (true) {
                                val view = binding.recycle
                                    .findViewHolderForLayoutPosition(reply!!.toFloor)?.itemView
                                delay(100)
                                (view ?: continue).apply {
                                    isPressed = true
                                    delay(200)
                                    isPressed = false
                                }
                                break
                            }
                        }
                    }
                }
                likeButton.setOnClickListener { model.like(this) }
            },
            postInit = {
                post = model.post.value
                root.setOnClickListener {
                    binding.replyHint.hint = "回复原帖"
                    binding.reply.tag = "0"
                    val params = binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                    val behavior = params.behavior as HideBottomViewOnScrollBehavior
                    behavior.slideUp(binding.bottomBar)
                    binding.reply.requestFocus()
                    getSystemService(InputMethodManager::class.java).showSoftInput(binding.reply, 0)
                }
                likeButton.setOnClickListener { model.like(this) }
                favourButton.setOnClickListener { model.favor(this) }
                executePendingBindings()
            },
            bottomInit = {
                loadMore.setOnClickListener {
                    model.more(this@PostDetailActivity)
                }
                noMore.setOnClickListener {
                    model.refresh(this@PostDetailActivity)
                    binding.recycle.smoothScrollToPosition(0)
                }
                networkError.setOnClickListener {
                    model.more(this@PostDetailActivity)
                }
                model.bottom.observe {
                    fun v(b: Boolean) = if (b) View.VISIBLE else View.INVISIBLE
                    binding.apply {
                        loadMore.visibility = v(it == BottomStatus.IDLE)
                        noMore.visibility = v(it == BottomStatus.NO_MORE)
                        networkError.visibility = v(it == BottomStatus.NETWORK_ERROR)
                        bottomLoading.visibility = v(it == BottomStatus.REFRESHING)
                    }
                }
            }
        )
        binding.apply {
            recycle.apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE &&
                            model.bottom.value == BottomStatus.IDLE &&
                            (layoutManager as LinearLayoutManager).run {
                                findLastVisibleItemPosition() >= itemCount - 2
                            }
                        ) model.more(context)
                        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) reply.clearFocus()
                        getSystemService(InputMethodManager::class.java)
                            .hideSoftInputFromWindow(reply.windowToken, 0)
                    }
                })
                viewTreeObserver.addOnPreDrawListener {
                    startPostponedEnterTransition()
                    true
                }
            }
            swipeRefresh.apply {
                setOnRefreshListener { model.refresh(context) }
            }
            replySubmit.setOnClickListener { model.reply(reply) }
            reply.addTextChangedListener { replyHint.isCounterEnabled = !it.isNullOrBlank() }
        }
        model.apply {
            post.observe {
                adapter.notifyItemChanged(0, it)
            }
            list.observe { adapter.submitList(listOf(null) + it + listOf(null)) }
            refresh.observe { binding.swipeRefresh.isRefreshing = it }
            info.observe {
                if (!it.isNullOrEmpty())
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).apply {
                        anchorView = binding.bottomBar
                        show()
                    }
            }
            sending.observe {
                binding.replySubmit.isEnabled = !it
                binding.replyProgress.visibility = if (it) View.VISIBLE else View.INVISIBLE
            }
            success.observe {
                if (it) {
                    binding.replyHint.hint = "回复原帖"
                    binding.reply.tag = "0"
                    val params = binding.bottomBar.layoutParams as CoordinatorLayout.LayoutParams
                    val behavior = params.behavior as HideBottomViewOnScrollBehavior
                    behavior.slideDown(binding.bottomBar)
                    getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(binding.reply.windowToken, 0)
                    binding.reply.clearFocus()
                    model.refresh(this@PostDetailActivity)
                    Snackbar.make(binding.swipeRefresh, "发送成功", Snackbar.LENGTH_SHORT).show()
                    binding.reply.setText("")
                }
            }
            more(this@PostDetailActivity)
        }
        TransitionSet().apply {
            addTransition(Slide(Gravity.END).apply {
                excludeTarget(binding.appbar, true)
            })
            addTransition(Fade().apply {
                addTarget(binding.appbar)
            })
        }.let {
            window.enterTransition = it
            window.returnTransition = it
        }
    }

    override fun finishAfterTransition() {
        setResult(RESULT_OK, Intent().apply {
            putExtra("post", model.post.value)
            putExtra("position", intent.getIntExtra("position", 0))
        })
        super.finishAfterTransition()
    }
}
