package org.wkfg.anonymous

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.wkfg.anonymous.databinding.PostCardBinding
import org.wkfg.anonymous.databinding.PostFilterBinding
import org.wkfg.anonymous.databinding.RecycleBottomBinding
import org.wkfg.anonymous.databinding.TagDialogBinding
import java.io.Serializable
import kotlin.time.ExperimentalTime

sealed class PostListElem : Serializable

object PostListFilter : PostListElem()
object PostListBottom : PostListElem()

data class Post constructor(
    var expanded: Boolean,
    val top: Boolean,
    val showInDetail: Boolean,
    val tag: Tag?,
    val id: String,
    val update: String,
    val post: String,
    val title: String,
    val content: String,
    var like: Like,
    var likeCount: Int,
    val replyCount: Int,
    var favor: Boolean?,
    val readCount: Int,
    val colorG: ColorG,
    val nameG: NameG,
    val myTag: Tag?,
    val unread: Boolean,
    val notification: String?,
) : PostListElem(), Serializable {
    enum class Tag(val display: String, val prefName: String, val backend: String) {
        SEX("性相关", "fold_sex", "sex"),
        POLITICS("政治相关", "fold_politics", "politics"),
        FAKE("未经证实", "fold_fake", "unproved"),
        BATTLE("引战", "fold_battle", "war"),
        UNCOMFORTABLE("令人不适", "fold_uncomfortable", "uncomfort"),
    }

    @ExperimentalTime
    constructor(json: JSONObject, showInDetail: Boolean) : this(
        expanded = false,
        top = json.optInt("WhetherTop") == 1,
        showInDetail = showInDetail,
        tag = Tag.values().firstOrNull { it.backend == json.getString("Tag") },
        id = json.getString("ThreadID"),
        update = json.getString("LastUpdateTime").display(),
        post = json.getString("PostTime").display(),
        title = json.getString("Title"),
        content = json.getString("Summary"),
        like = when (json.optInt("WhetherLike", -2)) {
            -1 -> Like.DISLIKE
            0 -> Like.NORMAL
            1 -> Like.LIKE
            -2 -> Like.LIKE_WAIT
            else -> error("")
        },
        likeCount = json.getInt("Like") - json.getInt("Dislike"),
        replyCount = json.getInt("Comment"),
        favor = json.has("WhetherFavour").takeIf { it }?.let { json.getInt("WhetherFavour") == 1 },
        readCount = json.getInt("Read"),
        colorG = ColorG(json.getString("ThreadID").toLong()),
        nameG = NameG(json.getString("AnonymousType"), json.getLong("RandomSeed")),
        myTag = Tag.values().firstOrNull { it.backend == json.optString("MyTag") },
        unread = json.optInt("Judge", 1) == 0,
        notification = with(json.optInt("Judge", 1) == 0) {
            when (val count = json.optInt("Type", -1)) {
                -1 -> null
                0 -> if (this) "有新回复" else "已读回复"
                else -> if (this) "新增 $count 个赞" else "$count 人赞了"
            }
        }
    )

    val avatar = colorG[0]
    fun avatarC() = nameG[0].split(" ").last()[0].toString()

    fun id() = if (showInDetail) nameG[0] else "#$id"
    fun update() = if (showInDetail) post else update

    fun titleWithLink(context: Context): CharSequence = SpannableStringBuilder().apply {
        if (top) append("置顶", TagSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimaryVariant, this, true)
            data
        }, TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorOnPrimary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (tag != null) append(tag.display, TagSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorSecondary, this, true)
            data
        }, TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorOnSecondary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(SpannableString(title).apply { links(context as Activity) })
    }

    fun unread(context: Context): CharSequence = SpannableStringBuilder().apply {
        if (notification != null) {
            if (unread) {
                append("未读", TagSpan(TypedValue().run {
                    context.theme.resolveAttribute(R.attr.colorPrimaryVariant, this, true)
                    data
                }, TypedValue().run {
                    context.theme.resolveAttribute(R.attr.colorOnPrimary, this, true)
                    data
                }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("$notification", ForegroundColorSpan(TypedValue().run {
                    context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
                    data
                }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else append("$notification")
        }
    }

    fun tags(context: Context): CharSequence = SpannableStringBuilder().apply {
        if (unread) append("未读", TagSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimaryVariant, this, true)
            data
        }, TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorOnPrimary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (top) append("置顶", TagSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
            data
        }, TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorOnPrimary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(tag?.display ?: "点踩较多", TagSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorSecondary, this, true)
            data
        }, TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorOnSecondary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (notification != null) append("$notification", ForegroundColorSpan(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
            data
        }), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(' ')
    }

    fun contentWithLink(context: Context) =
        if (showInDetail) SpannableString(content).apply { links(context as Activity) } else content

    fun likeCount() = likeCount.toString()
    fun replyCount() = replyCount.toString()
    fun readCount() = readCount.toString()
    fun titleMaxLines() = Int.MAX_VALUE
    fun contentMaxLines() = if (showInDetail) Int.MAX_VALUE else 4

    @ExperimentalUnsignedTypes
    private fun iconTint(context: Context, boolean: Boolean) = ColorStateList.valueOf(
        if (boolean) TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
            data
        } else TypedValue().run {
            context.theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            data
        }
    )

    fun likeIcon(context: Context) = ContextCompat.getDrawable(
        context,
        when (like) {
            Like.LIKE, Like.LIKE_WAIT -> R.drawable.ic_thumb_up_alt
            Like.NORMAL -> R.drawable.ic_thumb_up_alt_outlined
            Like.DISLIKE, Like.DISLIKE_WAIT -> R.drawable.ic_thumb_down_alt
        }
    )

    @ExperimentalUnsignedTypes
    fun likeIconTint(context: Context) = iconTint(
        context, when (like) {
            Like.LIKE, Like.DISLIKE -> true
            else -> false
        }
    )

    @ExperimentalUnsignedTypes
    fun replyIconTint(context: Context) = iconTint(context, false)

    @ExperimentalUnsignedTypes
    fun readIconTint(context: Context) = iconTint(context, false)

    @ExperimentalUnsignedTypes
    fun menuIconTint(context: Context) = iconTint(context, false)

    enum class Category(val id: Int) {
        ALL(0),
        CAMPUS(1),
        ENTERTAINMENT(2),
        EMOTION(3),
        SCIENCE(4),
        IT(5),
        SOCIAL(6),
        MUSIC(7),
        MOVIE(8),
        ART(9),
        LIFE(10),
    }
}

class PostAdapter(
    private val filterInit: PostFilterBinding.() -> Unit,
    private val postInit: PostCardBinding.() -> Unit,
    private val bottomInit: RecycleBottomBinding.() -> Unit,
) : ListAdapter<PostListElem, PostAdapter.ViewHolder>(PostDiffCallback) {
    companion object {
        const val FILTER = 1
        const val POST = 0
        const val BOTTOM = 2
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class PostCard(
            private val binding: PostCardBinding,
            private val init: PostCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind(item: Post) {
                binding.apply {
                    post = item
                    init()
                    executePendingBindings()
                }
            }
        }

        class Filter(
            binding: PostFilterBinding,
            init: PostFilterBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            init {
                binding.init()
            }
        }

        class Bottom(
            binding: RecycleBottomBinding,
            init: RecycleBottomBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            init {
                binding.init()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            POST -> {
                val binding = PostCardBinding.inflate(inflater, parent, false)
                ViewHolder.PostCard(binding, postInit)
            }
            FILTER -> {
                val binding = PostFilterBinding.inflate(inflater, parent, false)
                ViewHolder.Filter(binding, filterInit)
            }
            BOTTOM -> {
                val binding = RecycleBottomBinding.inflate(inflater, parent, false)
                ViewHolder.Bottom(binding, bottomInit)
            }
            else -> error("")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder.PostCard -> holder.bind(getItem(position) as Post)
            is ViewHolder.Filter -> Unit
            is ViewHolder.Bottom -> Unit
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ViewHolder.PostCard)
            (payloads.singleOrNull() as? Post)?.let { holder.bind(it) }
                ?: super.onBindViewHolder(holder, position, payloads)
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            PostListFilter -> FILTER
            PostListBottom -> BOTTOM
            is Post -> POST
        }
    }
}

object PostDiffCallback : DiffUtil.ItemCallback<PostListElem>() {
    override fun areItemsTheSame(oldItem: PostListElem, newItem: PostListElem) =
        oldItem == newItem || (oldItem is Post && newItem is Post && oldItem.id == newItem.id)

    override fun areContentsTheSame(oldItem: PostListElem, newItem: PostListElem) =
        oldItem == newItem
}

@BindingAdapter("magic")
fun CardView.magic(post: Post) {
    val binding = DataBindingUtil.getBinding<PostCardBinding>(this)!!
    val context = context
    val folded = !post.showInDetail && !post.expanded && (
            (post.likeCount <= -5 &&
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .getString("fold_thumb_down", "fold") == "fold") ||
                    post.tag?.let {
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(it.prefName, "fold") == "fold"
                    } ?: false)
    binding.expanded.visibility = if (folded) View.GONE else View.VISIBLE
    binding.folded.visibility = if (folded) View.VISIBLE else View.GONE
    val showMenu = {
        val spannable = SpannableString("${post.title}\n${post.content}").apply {
            setSpan(
                TextAppearanceSpan(context, R.attr.textAppearanceSubtitle1),
                0, post.title.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0, post.title.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val links = spannable.links(context as Activity)
        val extraLinks = if (post.showInDetail) {
            if (post.like == Like.NORMAL) arrayOf("踩") else arrayOf()
        } else (listOf("举报", "屏蔽", "建议标签") + links.map { (it, _) -> "跳转到 $it" }).toTypedArray()
        val items = arrayOf(
            "复制标题", "复制内容", "自由复制", *extraLinks
        )
        MaterialAlertDialogBuilder(context).apply {
            setItems(items) { _: DialogInterface, i: Int ->
                when (i) {
                    0 -> copy(context, post.title)
                    1 -> copy(context, post.content)
                    2 -> showSelectDialog(context, spannable)
                    3 -> when (context) {
                        is MainActivity -> context.showReport(post.id) {
                            context.model.report(context, post)
                        }
                        is PostDetailActivity -> context.model.dislike(binding)
                    }
                    4 -> {
                        val block = {
                            with(
                                context.getSharedPreferences("blocked", Context.MODE_PRIVATE).edit()
                            ) {
                                putBoolean(post.id, true)
                                putBoolean("notified", true)
                                commit()
                            }
                            Snackbar.make(
                                (context as MainActivity).binding.swipeRefresh, "屏蔽成功",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        if (!context.getSharedPreferences("blocked", Context.MODE_PRIVATE)
                                .getBoolean("notified", false)
                        ) MaterialAlertDialogBuilder(context).apply {
                            setTitle("屏蔽说明")
                            setMessage("被屏蔽的帖子ID暂时仅保存在本地，清除数据或更换设备会丢失，对于已被屏蔽的帖子，暂时仅支持直接通过链接跳转查看")
                            setPositiveButton("知道了(p≧w≦q)") { _, _ -> block() }
                            setNeutralButton("那算了(；′⌒`)", null)
                            setCancelable(true)
                            show()
                        }
                        else block()
                    }
                    5 -> (context as? MainActivity)?.apply {
                        showTag(post.id, post.myTag) { model.tag(this, post, it) }
                    }
                    else -> links[i - 6].second()
                }
            }
            show()
        }
    }
    binding.menu.setOnClickListener { showMenu() }
    setOnLongClickListener {
        if (folded) {
            MaterialAlertDialogBuilder(context).apply {
                setItems(
                    post.tag?.display?.let { arrayOf("展开", "直接隐藏 $it") }
                        ?: arrayOf("展开")) { _, it ->
                    when (it) {
                        0 -> this@magic.performClick()
                        1 -> {
                            with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                                putString(post.tag!!.prefName, "hide")
                                commit()
                            }
                            (context as? MainActivity)?.apply {
                                model.list.value = model.list.value?.filter { it.tag != post.tag }
                                Snackbar.make(
                                    this.binding.swipeRefresh, "已直接隐藏 ${post.tag!!.display}",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                show()
            }
        } else showMenu()
        true
    }
    if (post.showInDetail) for (view in listOf(binding.title, binding.content)) view.apply {
        movementMethod = MagicClickableMovementMethod
        isClickable = false
        isLongClickable = false
    }
    if (post.showInDetail) binding.likeButton.apply {
        isClickable = true
        isFocusable = true
        rippleColor = ColorStateList.valueOf(TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorControlHighlight, this, true)
            data
        })
    }
    else binding.likeButton.apply {
        isClickable = false
        isFocusable = false
        rippleColor = ColorStateList.valueOf(0xffffff)
    }
    binding.unread.visibility =
        if (binding.post!!.showInDetail || binding.post!!.notification == null) View.GONE
        else View.VISIBLE
    binding.title.requestLayout()
    binding.title.invalidate()
    binding.content.requestLayout()
    binding.content.invalidate()
    binding.buttons.requestLayout()
    binding.buttons.invalidate()
}

class PostListViewModel : ViewModel() {
    val list = MutableLiveData(listOf<Post>())
    val info = MutableLiveData("")
    val refresh = MutableLiveData(false)
    var bottom = MutableLiveData(BottomStatus.REFRESHING)

    enum class Category(
        val display: String, val category: Post.Category, val type: Network.PostType
    ) {
        ALL("主干道", Post.Category.ALL, Network.PostType.TIME),
        HOT("热门帖子", Post.Category.ALL, Network.PostType.TRENDING),
        CAMPUS("校园", Post.Category.CAMPUS, Network.PostType.TIME),
        ENTERTAIN("娱乐", Post.Category.ENTERTAINMENT, Network.PostType.TIME),
        EMOTION("情感", Post.Category.EMOTION, Network.PostType.TIME),
        SCIENCE("科学", Post.Category.SCIENCE, Network.PostType.TIME),
        IT("数码", Post.Category.IT, Network.PostType.TIME),
        SOCIAL("社会", Post.Category.SOCIAL, Network.PostType.TIME),
        MUSIC("音乐", Post.Category.MUSIC, Network.PostType.TIME),
        MOVIE("影视", Post.Category.MOVIE, Network.PostType.TIME),
        ART("文史哲", Post.Category.ART, Network.PostType.TIME),
        LIFE("人生经验", Post.Category.LIFE, Network.PostType.TIME),
        MY("我的发帖", Post.Category.ALL, Network.PostType.MY),
        UNREAD("我的通知", Post.Category.ALL, Network.PostType.MESSAGE),
        FAVOR("我的收藏", Post.Category.ALL, Network.PostType.FAVOURED)
    }

    val bottomCategory = listOf(Category.HOT, Category.UNREAD)
    val category = MutableLiveData(Category.ALL)
    var updateNav = false
    val search = MutableLiveData<String>(null)
    private var refreshingJob: Job? = null
    private var last = "NULL"

    @ExperimentalTime
    fun refresh(context: Context) {
        refreshingJob?.cancel(CancellationException())
        refreshingJob = viewModelScope.launch {
            refresh.value = true
            delay(300)
            try {
                val tot = mutableListOf<Post>()
                last = "NULL"
                for (rep in 1..5) {
                    if (tot.size >= 8) break
                    val (last, new) =
                        if (search.value.isNullOrBlank()) Network.fetchPost(
                            category.value!!.type, category.value!!.category, last
                        ) else Network.search(search.value!!, last)
                    this@PostListViewModel.last = last
                    if (new.isEmpty()) break
                    tot.addAll(
                        if (category.value in listOf(Category.MY, Category.FAVOR, Category.UNREAD))
                            new.onEach { it.expanded = true }
                        else new.filter {
                            (it.tag == null || PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(it.tag.prefName, "fold") != "hide") &&
                                    !context.getSharedPreferences("blocked", Context.MODE_PRIVATE)
                                        .getBoolean(it.id, false) &&
                                    (it.likeCount > -5 ||
                                            PreferenceManager.getDefaultSharedPreferences(context)
                                                .getString("fold_thumb_down", "fold") != "hide")
                        })
                }
                list.value = tot
                delay(100)
                bottom.value = if (tot.size < 8) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: Network.BannedException) {
                e.showLogout(context)
            } catch (e: CancellationException) {
                refresh.value = false
            } catch (e: Exception) {
                info.value = "网络错误"
                bottom.value = BottomStatus.NETWORK_ERROR
            } finally {
                refresh.value = false
            }
        }
    }

    @ExperimentalTime
    fun more(context: Context) {
        refreshingJob?.cancel(CancellationException())
        refreshingJob = viewModelScope.launch {
            try {
                bottom.value = BottomStatus.REFRESHING
                delay(300)
                var newCount = 0
                for (rep in 1..5) {
                    if (newCount >= 8) break
                    val (last, new) =
                        if (search.value.isNullOrBlank()) Network.fetchPost(
                            category.value!!.type, category.value!!.category, last
                        ) else Network.search(search.value!!, last)
                    this@PostListViewModel.last = last
                    if (new.isEmpty()) break
                    val newFiltered =
                        if (category.value in listOf(Category.MY, Category.FAVOR, Category.UNREAD))
                            new.onEach { it.expanded = true }
                        else new.filter {
                            (it.tag == null || PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(it.tag.prefName, "fold") != "hide") &&
                                    !context.getSharedPreferences("blocked", Context.MODE_PRIVATE)
                                        .getBoolean(it.id, false)
                        }
                    list.value = list.value!! + newFiltered
                    newCount += newFiltered.size
                }
                delay(100)
                bottom.value = if (newCount < 8) BottomStatus.NO_MORE else BottomStatus.IDLE
            } catch (e: Network.NotLoggedInException) {
                context.needLogin()
            } catch (e: Network.BannedException) {
                e.showLogout(context)
            } catch (e: CancellationException) {
                bottom.value = BottomStatus.IDLE
            } catch (e: Exception) {
                bottom.value = BottomStatus.NETWORK_ERROR
            } finally {
                if (bottom.value == BottomStatus.REFRESHING)
                    bottom.value = BottomStatus.NETWORK_ERROR
            }
        }
    }

    fun report(context: Context, post: Post) = viewModelScope.launch {
        try {
            Network.report(post.id)
            info.value = "举报成功"
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun tag(context: Context, post: Post, tag: Post.Tag) = viewModelScope.launch {
        try {
            Network.tag(post.id, tag)
            info.value = "已建议标记为 ${tag.display}"
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: Network.BannedException) {
            e.showLogout(context)
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }
}

fun Context.showReport(id: String, report: () -> Unit) =
    MaterialAlertDialogBuilder(this).apply {
        setTitle("举报 #$id")
        setMessage("确定要举报吗？\n帖子被举报数次后将被屏蔽，我们一起维护无可奉告论坛环境。")
        setPositiveButton("! 确认举报 !") { _, _ -> report() }
        setNegativeButton("> 手滑了 <", null)
        setCancelable(true)
        show()
    }

fun Activity.showTag(id: String, myTag: Post.Tag?, tag: (Post.Tag) -> Unit) =
    MaterialAlertDialogBuilder(this).apply {
        setCustomTitle(TagDialogBinding.inflate(layoutInflater).run {
            title.text = "为 #$id 建议标签"
            message.text =
                "被数个用户建议后，帖子将自动被加上相应标签，并在选择折叠/屏蔽这个标签的用户处正确地被折叠/屏蔽。${myTag?.display?.let { "\n\n您已建议将其标记为 $it 。" } ?: ""}"
            root
        })
        setItems(Post.Tag.values().map { it.display }.toTypedArray()) { _, i ->
            tag(Post.Tag.values()[i])
        }
        setCancelable(true)
        show()
    }
