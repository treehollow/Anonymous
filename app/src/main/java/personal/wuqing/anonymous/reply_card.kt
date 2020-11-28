package personal.wuqing.anonymous

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import personal.wuqing.anonymous.databinding.PostCardBinding
import personal.wuqing.anonymous.databinding.RecycleBottomBinding
import personal.wuqing.anonymous.databinding.ReplyCardBinding
import java.io.Serializable
import kotlin.time.ExperimentalTime

@ExperimentalTime
data class Reply constructor(
    val id: String,
    val update: String,
    val name: String,
    val avatar: Int,
    val content: String,
    var like: Boolean,
    var likeCount: Int,
    val toName: String,
    val toFloor: Int
) : Serializable {
    constructor(json: JSONObject, nameG: NameG, colorG: ColorG) : this(
        id = json.getString("FloorID"),
        update = json.getString("RTime").untilNow().display(),
        name = nameG[json.getString("Speakername").toInt()],
        avatar = colorG[json.getString("Speakername").toInt()],
        content = json.getString("Context"),
        like = json.getInt("WhetherLike") == 1,
        likeCount = json.getInt("Like"),
        toName = nameG[json.getString("Replytoname").toInt()],
        toFloor = json.getInt("Replytofloor"),
    )

    fun avatarC() = name.split(" ").last()[0].toString()
    fun id() = "#$id"
    fun likeCount() = likeCount.toString()
    fun showTo() = toFloor != 0
    fun showReply() = if (showTo()) "回复" else ""
    fun toName() = if (showTo()) toName else ""
    fun toFloor() = if (showTo()) "#$toFloor" else ""
    fun likeIcon(context: Context) = ContextCompat.getDrawable(
        context, if (like) R.drawable.ic_thumb_up else R.drawable.ic_thumb_up_outlined
    )

    fun contentWithLink() = SpannableString(content).apply { links() }

    @ExperimentalUnsignedTypes
    fun likeIconTint(context: Context) = ColorStateList.valueOf(
        if (like) TypedValue().run {
            context.theme.resolveAttribute(R.attr.colorPrimary, this, true)
            data
        } else TypedValue().run {
            context.theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            data.toString(16).padStart(3, '0').toCharArray()
                .joinToString("", prefix = "ff") { "$it$it" }
                .toUInt(16).toInt()
        }
    )
}

@ExperimentalTime
class ReplyAdapter(
    private val replyInit: ReplyCardBinding.() -> Unit,
    private val postInit: PostCardBinding.() -> Unit,
    private val bottomInit: RecycleBottomBinding.() -> Unit,
) : ListAdapter<Reply, ReplyAdapter.ViewHolder>(ReplyDiffCallback) {
    companion object {
        const val POST = 1
        const val REPLY = 0
        const val BOTTOM = 2
    }

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class ReplyCard(
            private val binding: ReplyCardBinding,
            private val init: ReplyCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind(item: Reply) = binding.apply {
                reply = item
                init()
                executePendingBindings()
            }
        }

        class PostCard(
            private val binding: PostCardBinding,
            private val init: PostCardBinding.() -> Unit
        ) : ViewHolder(binding.root) {
            fun bind() = binding.init()
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
            REPLY -> {
                val binding = ReplyCardBinding.inflate(inflater, parent, false)
                ViewHolder.ReplyCard(binding, replyInit)
            }
            POST -> {
                val binding = PostCardBinding.inflate(inflater, parent, false)
                ViewHolder.PostCard(binding, postInit)
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
            is ViewHolder.ReplyCard -> holder.bind(getItem(position))
            is ViewHolder.PostCard -> holder.bind()
            is ViewHolder.Bottom -> Unit
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ViewHolder.PostCard && payloads.singleOrNull() is Post) holder.bind()
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> POST
            currentList.size - 1 -> BOTTOM
            else -> REPLY
        }
    }
}

@ExperimentalTime
object ReplyDiffCallback : DiffUtil.ItemCallback<Reply>() {
    override fun areItemsTheSame(oldItem: Reply, newItem: Reply): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Reply, newItem: Reply): Boolean {
        return oldItem == newItem
    }
}

@ExperimentalTime
@BindingAdapter("textMagic")
fun CardView.textMagic(reply: Reply) {
    val binding = DataBindingUtil.getBinding<ReplyCardBinding>(this)!!
    val context = context
    val showMenu = {
        val spannable = SpannableString(reply.content).apply { links() }
        val items = arrayOf(
            "回复", "复制内容", "自由复制"
        )
        MaterialAlertDialogBuilder(context).apply {
            setItems(items) { _: DialogInterface, i: Int ->
                when (i) {
                    0 -> binding.root.performClick()
                    1 -> copy(context, reply.content)
                    2 -> showSelectDialog(context, spannable)
                }
            }
            show()
        }
        true
    }
    setOnLongClickListener { showMenu() }
    binding.content.apply {
        movementMethod = MagicClickableMovementMethod
        isClickable = false
        isLongClickable = false
    }
}

@ExperimentalTime
class ReplyListViewModel : ViewModel() {
    val post = MutableLiveData<Post>()
    val list = MutableLiveData(listOf<Reply>())
    val info = MutableLiveData("")
    val refresh = MutableLiveData(false)
    var bottom = MutableLiveData(BottomStatus.REFRESHING)
    val sending = MutableLiveData(false)
    val success = MutableLiveData(false)
    private var last = "NULL"

    fun refresh(context: Context) = viewModelScope.launch {
        refresh.value = true
        delay(300)
        try {
            val (pair, newList) = Network.fetchReply(post.value!!.id)
            val (last, newPost) = pair
            this@ReplyListViewModel.last = last
            post.value = newPost
            list.value = newList
            delay(100)
            bottom.value =
                if (list.value.isNullOrEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: CancellationException) {
            refresh.value = false
        } catch (e: Exception) {
            info.value = "网络错误"
            bottom.value = BottomStatus.NETWORK_ERROR
        } finally {
            refresh.value = false
        }
    }

    fun more(context: Context) = viewModelScope.launch {
        bottom.value = BottomStatus.REFRESHING
        delay(300)
        try {
            val (pair, newList) = Network.fetchReply(post.value!!.id, last)
            val (last, newPost) = pair
            this@ReplyListViewModel.last = last
            post.value = newPost
            list.value = list.value!! + newList
            delay(100)
            bottom.value = if (newList.isEmpty()) BottomStatus.NO_MORE else BottomStatus.IDLE
        } catch (e: Network.NotLoggedInException) {
            context.needLogin()
        } catch (e: CancellationException) {
            bottom.value = BottomStatus.IDLE
        } catch (e: Exception) {
            bottom.value = BottomStatus.NETWORK_ERROR
        }
    }

    fun like(binding: ReplyCardBinding) = viewModelScope.launch {
        try {
            binding.reply?.apply {
                if (like) {
                    Network.unlikeReply(post.value!!.id, id)
                    like = false
                    likeCount--
                } else {
                    Network.likeReply(post.value!!.id, id)
                    like = true
                    likeCount++
                }
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun reply(editText: EditText) = viewModelScope.launch {
        if (editText.text.toString().isBlank()) {
            info.value = "发送失败：内容为空"
            return@launch
        }
        sending.value = true
        delay(300)
        try {
            Network.reply(post.value!!.id, editText.tag as String, editText.text.toString())
            success.value = true
        } catch (e: Network.NotLoggedInException) {
            editText.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        } finally {
            sending.value = false
        }
    }

    fun like(binding: PostCardBinding) = viewModelScope.launch {
        try {
            binding.post?.apply {
                if (like) {
                    Network.unlikePost(id)
                    like = false
                    likeCount--
                } else {
                    Network.likePost(id)
                    like = true
                    likeCount++
                }
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }

    fun favor(binding: PostCardBinding) = viewModelScope.launch {
        try {
            binding.post?.apply {
                if (favor) Network.deFavorPost(id) else Network.favorPost(id)
                favor = !favor
            }
            binding.invalidateAll()
        } catch (e: Network.NotLoggedInException) {
            binding.root.context.needLogin()
        } catch (e: Exception) {
            info.value = "网络错误"
        }
    }
}
