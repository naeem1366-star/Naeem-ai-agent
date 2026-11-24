package com.naeem.ai

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class StudioTrack(
    val id:String, val title:String, val artist:String, val album:String,
    val url:String, val source:String, val license:String=""
)
private data class StudioAlbum(val id:String,val name:String,val tracks:List<StudioTrack>)

private class MusicStudioStore(context:Context) {
    private val prefs=context.getSharedPreferences("supra_music_studio",Context.MODE_PRIVATE)
    fun clientId()=prefs.getString("jamendo_client_id","").orEmpty()
    fun saveClientId(value:String){prefs.edit().putString("jamendo_client_id",value.trim()).apply()}
    fun load():List<StudioAlbum>{
        val root=runCatching{JSONArray(prefs.getString("albums","[]"))}.getOrElse{JSONArray()}
        return (0 until root.length()).mapNotNull{i->runCatching{
            val a=root.getJSONObject(i);val arr=a.optJSONArray("tracks")?:JSONArray()
            StudioAlbum(a.optString("id"),a.optString("name"),(0 until arr.length()).mapNotNull{j->
                val t=arr.optJSONObject(j)?:return@mapNotNull null
                val url=t.optString("url")
                if(!url.startsWith("https://")) null else StudioTrack(t.optString("id"),t.optString("title"),t.optString("artist"),t.optString("album"),url,t.optString("source"),t.optString("license"))
            })
        }.getOrNull()}
    }
    fun save(albums:List<StudioAlbum>){
        val root=JSONArray()
        albums.forEach{a->
            val arr=JSONArray()
            a.tracks.forEach{t->arr.put(JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist).put("album",t.album).put("url",t.url).put("source",t.source).put("license",t.license))}
            root.put(JSONObject().put("id",a.id).put("name",a.name).put("tracks",arr))
        }
        prefs.edit().putString("albums",root.toString()).apply()
    }
}

private class MultiMusicSearch {
    private val http=OkHttpClient()
    suspend fun search(query:String,source:String,clientId:String):Pair<List<StudioTrack>,List<String>> = withContext(Dispatchers.IO){
        val tracks=mutableListOf<StudioTrack>();val errors=mutableListOf<String>()
        if(source=="همه"||source=="iTunes") runCatching{
            val q=URLEncoder.encode(query,"UTF-8")
            http.newCall(Request.Builder().url("https://itunes.apple.com/search?term=$q&entity=song&limit=25").build()).execute().use{r->
                if(!r.isSuccessful) error("iTunes پاسخ نداد")
                val a=JSONObject(r.body?.string().orEmpty()).optJSONArray("results")?:JSONArray()
                (0 until a.length()).mapNotNull{i->
                    val o=a.optJSONObject(i)?:return@mapNotNull null;val url=o.optString("previewUrl")
                    if(!url.startsWith("https://")) null else StudioTrack("itunes_"+o.optString("trackId"),o.optString("trackName"),o.optString("artistName"),o.optString("collectionName"),url,"iTunes","پیش‌نمایش رسمی")
                }
            }.also{tracks.addAll(it)}
        }.onFailure{errors.add("iTunes: "+(it.message?:"خطا"))}
        if(source=="همه"||source=="Jamendo"){
            if(clientId.isBlank()) errors.add("برای جست‌وجوی Jamendo، شناسه رایگان را از developer.jamendo.com بگیر و در تنظیمات وارد کن.")
            else runCatching{
                val q=URLEncoder.encode(query,"UTF-8");val key=URLEncoder.encode(clientId,"UTF-8")
                val endpoint="https://api.jamendo.com/v3.0/tracks/?client_id=$key&format=json&limit=25&search=$q&audioformat=mp32&include=licenses"
                http.newCall(Request.Builder().url(endpoint).build()).execute().use{r->
                    if(!r.isSuccessful) error("Jamendo پاسخ نداد")
                    val a=JSONObject(r.body?.string().orEmpty()).optJSONArray("results")?:JSONArray()
                    (0 until a.length()).mapNotNull{i->
                        val o=a.optJSONObject(i)?:return@mapNotNull null;val url=o.optString("audio")
                        if(!url.startsWith("https://")) null else StudioTrack("jamendo_"+o.optString("id"),o.optString("name"),o.optString("artist_name"),o.optString("album_name"),url,"Jamendo",o.optString("license_ccurl").ifBlank{"مجوز اثر را بررسی کن"})
                    }
                }.also{tracks.addAll(it)}
            }.onFailure{errors.add("Jamendo: "+(it.message?:"خطا"))}
        }
        tracks.distinctBy{it.id} to errors
    }
}

private suspend fun prepareMixPlayer(url:String):MediaPlayer=suspendCancellableCoroutine{c->
    val p=MediaPlayer()
    c.invokeOnCancellation{runCatching{p.release()}}
    p.setOnPreparedListener{if(c.isActive)c.resume(p) else runCatching{p.release()}}
    p.setOnErrorListener{_,what,extra->
        if(c.isActive)c.resumeWithException(IllegalStateException("خطای پخش صدا: $what/$extra"))
        true
    }
    runCatching{p.setDataSource(url);p.prepareAsync()}.onFailure{
        runCatching{p.release()};if(c.isActive)c.resumeWithException(it)
    }
}

@Composable
fun MusicStudioDashboard(onClose:()->Unit,onAskAi:(String)->Unit){
    val context= LocalContext.current
    val store=remember(context){MusicStudioStore(context)}
    val search=remember{MultiMusicSearch()}
    val scope= rememberCoroutineScope()
    val albums= remember{mutableStateListOf<StudioAlbum>().apply{addAll(store.load())}}
    val selected= remember{mutableStateListOf<StudioTrack>()}
    var tab by remember{mutableStateOf("کشف آهنگ")}
    var query by remember{mutableStateOf("")}
    var source by remember{mutableStateOf("همه")}
    var results by remember{mutableStateOf<List<StudioTrack>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var clientId by remember{mutableStateOf(store.clientId())}
    var settings by remember{mutableStateOf(false)}
    var albumDialog by remember{mutableStateOf(false)}
    var albumName by remember{mutableStateOf("")}
    var mixing by remember{mutableStateOf(false)}
    var nowPlaying by remember{mutableStateOf("")}
    var mixJob by remember{mutableStateOf<Job?>(null)}
    var activePlayer by remember{mutableStateOf<MediaPlayer?>(null)}

    fun stopMix(){
        mixJob?.cancel();mixJob=null
        runCatching{activePlayer?.stop()};runCatching{activePlayer?.release()}
        activePlayer=null;mixing=false;nowPlaying=""
    }
    fun playMix(queue:List<StudioTrack>){
        if(queue.isEmpty()){message="اول آهنگ‌ها را انتخاب کن.";return}
        stopMix()
        mixJob=scope.launch{
            var current:MediaPlayer?=null
            try{
                mixing=true
                current=prepareMixPlayer(queue.first().url);activePlayer=current
                current.setVolume(1f,1f);current.start();nowPlaying=queue.first().title
                for(i in 0 until queue.lastIndex){
                    delay(26_000)
                    val next=prepareMixPlayer(queue[i+1].url)
                    next.setVolume(0f,0f);next.start();nowPlaying=queue[i+1].title
                    repeat(40){step->
                        val incoming=(step+1)/40f;val outgoing=1f-incoming
                        runCatching{current?.setVolume(outgoing,outgoing)}
                        runCatching{next.setVolume(incoming,incoming)}
                        delay(100)
                    }
                    runCatching{current?.stop()};runCatching{current?.release()}
                    current=next;activePlayer=next
                }
                delay(26_000)
                message="میکس تمام شد."
            }catch(e:CancellationException){throw e}
            catch(e:Exception){message="پخش متوقف شد: "+(e.message?:"خطای صوتی")}
            finally{
                runCatching{current?.stop()};runCatching{current?.release()}
                if(activePlayer===current)activePlayer=null
                mixing=false;nowPlaying=""
            }
        }
    }
    DisposableEffect(Unit){onDispose{mixJob?.cancel();runCatching{activePlayer?.release()}}}

    Column(Modifier.fillMaxSize().background(Color(0xFF111318)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("SUPRA MUSIC STUDIO",color=Color(0xFFD4D9E0),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text("موسیقی و میز دی‌جی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("جست‌وجو، میکس خودکار و آلبوم‌های شخصی",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={stopMix();onClose()}){Icon(Icons.Default.Close,null,tint=Color.White)}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
            listOf("کشف آهنگ","میز دی‌جی","آلبوم‌ها","ساخت موسیقی").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}
            Spacer(Modifier.weight(1f))
            IconButton(onClick={settings=true}){Icon(Icons.Default.Tune,"تنظیم منابع",tint=Color(0xFFD4D9E0))}
        }
        if(mixing) Surface(color=Color(0xFF252B35),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.GraphicEq,null,tint=Color(0xFFD4D9E0));Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)){Text("میکس در حال پخش",color=Color.White,fontWeight=FontWeight.Bold);Text(nowPlaying,color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)}
                IconButton(onClick={stopMix()}){Icon(Icons.Default.Stop,"توقف",tint=Color.White)}
            }
        }
        if(message.isNotBlank()) Text(message,color=Color(0xFFD4D9E0),style=MaterialTheme.typography.bodySmall)
        when(tab){
            "ساخت موسیقی"-> MusicCreationPanel(onMessage={message=it})
            "کشف آهنگ"->{
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("نام آهنگ، خواننده یا سبک")})
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    listOf("همه","iTunes","Jamendo").forEach{FilterChip(selected=source==it,onClick={source=it},label={Text(it)})}
                    Spacer(Modifier.weight(1f))
                    Button(enabled=query.isNotBlank()&&!loading,onClick={
                        loading=true;message=""
                        scope.launch{try{
                            val(found,errors)=search.search(query,source,clientId)
                            results=found;message=if(errors.isEmpty())"${found.size} آهنگ پیدا شد." else errors.joinToString(" | ")
                        }catch(e:Exception){message=e.message?:"جست‌وجو ناموفق بود"}finally{loading=false}}
                    }){Icon(Icons.Default.Search,null);Text("جست‌وجو")}
                }
                if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Color(0xFFD4D9E0))
                Text("برای اضافه‌کردن به صف دی‌جی، علامت + را بزن.",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    items(results){track->
                        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202630))){
                            Row(Modifier.fillMaxWidth().padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){
                                    Text(track.title,color=Color.White,fontWeight=FontWeight.SemiBold)
                                    Text(track.artist+" • "+track.album,color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)
                                    Text(track.source+" • "+track.license,color=Color(0xFFBFC7D2),style=MaterialTheme.typography.labelSmall,maxLines=1)
                                }
                                IconButton(onClick={if(selected.none{it.id==track.id})selected.add(track)else message="این آهنگ قبلاً انتخاب شده."}){Icon(Icons.Default.AddCircle,"افزودن",tint=Color(0xFFD4D9E0))}
                                IconButton(onClick={playMix(listOf(track))}){Icon(Icons.Default.PlayArrow,"پخش نمونه",tint=Color.White)}
                            }
                        }
                    }
                }
            }
            "میز دی‌جی"->{
                Text("صف میکس • ${selected.size} آهنگ",color=Color.White,fontWeight=FontWeight.Bold)
                Text("هر آهنگ حدود ۳۰ ثانیه؛ چهار ثانیه پایانی با آهنگ بعدی هم‌پوشانی دارد.",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(enabled=selected.isNotEmpty()&&!mixing,onClick={playMix(selected.toList())},modifier=Modifier.weight(1f)){Icon(Icons.Default.PlayArrow,null);Text("شروع میکس")}
                    OutlinedButton(onClick={stopMix();selected.clear()},modifier=Modifier.weight(1f)){Text("پاک‌کردن صف")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(enabled=selected.isNotEmpty(),onClick={albumDialog=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.Save,null);Text("ذخیره آلبوم")}
                    OutlinedButton(enabled=selected.size>1,onClick={selected.reverse()},modifier=Modifier.weight(1f)){Text("معکوس‌کردن ترتیب")}
                }
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    items(selected){track->
                        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202630))){
                            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(track.title,color=Color.White,fontWeight=FontWeight.SemiBold);Text(track.artist+" • "+track.source,color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)}
                                IconButton(onClick={selected.remove(track)}){Icon(Icons.Default.RemoveCircleOutline,"حذف",tint=Color(0xFFFF9A91))}
                            }
                        }
                    }
                }
            }
            else->{
                if(albums.isEmpty())Text("هنوز آلبومی نساختی. آهنگ‌ها را انتخاب و از میز دی‌جی ذخیره کن.",color=Color(0xFFA8ADB8))
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    items(albums){album->
                        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202630))){
                            Column(Modifier.fillMaxWidth().padding(12.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){Text(album.name,color=Color.White,fontWeight=FontWeight.Bold);Text("${album.tracks.size} آهنگ",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)}
                                    IconButton(onClick={selected.clear();selected.addAll(album.tracks);tab="میز دی‌جی"}){Icon(Icons.Default.QueueMusic,"بازکردن",tint=Color(0xFFD4D9E0))}
                                    IconButton(onClick={playMix(album.tracks)}){Icon(Icons.Default.PlayArrow,"پخش آلبوم",tint=Color.White)}
                                    IconButton(onClick={albums.remove(album);store.save(albums.toList())}){Icon(Icons.Default.DeleteOutline,"حذف",tint=Color(0xFFFF9A91))}
                                }
                                Text(album.tracks.take(4).joinToString(" • "){it.title},color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall,maxLines=2)
                            }
                        }
                    }
                }
            }
        }
    }
    if(albumDialog)AlertDialog(
        onDismissRequest={albumDialog=false},title={Text("ذخیره آلبوم")},
        text={OutlinedTextField(albumName,{albumName=it},label={Text("مثلاً تولد، آرامش یا یوگا")},singleLine=true)},
        confirmButton={TextButton(onClick={
            if(albumName.isNotBlank()&&selected.isNotEmpty()){
                albums.add(0,StudioAlbum(UUID.randomUUID().toString(),albumName.trim(),selected.toList()))
                store.save(albums.toList());albumName="";albumDialog=false;tab="آلبوم‌ها";message="آلبوم ذخیره شد."
            }
        }){Text("ذخیره")}},
        dismissButton={TextButton(onClick={albumDialog=false}){Text("انصراف")}}
    )
    if(settings)AlertDialog(
        onDismissRequest={settings=false},title={Text("منابع جست‌وجوی موسیقی")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("iTunes بدون کلید قابل استفاده است. برای جست‌وجوی کاتالوگ Jamendo، شناسه رایگان توسعه‌دهنده را وارد کن.")
            OutlinedTextField(clientId,{clientId=it},label={Text("Jamendo Client ID")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii))
            Text("developer.jamendo.com",color=Color(0xFF8FAFD8),style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(onClick={store.saveClientId(clientId);settings=false;message="شناسه منبع ذخیره شد."}){Text("ذخیره")}},
        dismissButton={TextButton(onClick={settings=false}){Text("بستن")}}
    )
}


@Composable
private fun MusicCreationPanel(onMessage:(String)->Unit){
    val context=LocalContext.current
    val config=remember(context){SecureConfigStore(context)}
    val baseUrl=config.baseUrl()
    val apiKey=config.apiKeyFor(baseUrl).ifBlank{config.apiKey()}
    val api=remember(baseUrl,apiKey){NetArzApiClient(baseUrl,apiKey)}
    val scope=rememberCoroutineScope()
    var model by remember{mutableStateOf(config.modelFor("music").ifBlank{"lyria-3.5"})}
    var models by remember{mutableStateOf(listOf("lyria-3.5","lyria-3-pro-preview","lyria-3-clip-preview","openrouter/google/lyria-3-pro-preview","openrouter/google/lyria-3-clip-preview","eleven-music-v1","eleven-sound-effects-v3","eleven-sound-effects-v2"))}
    var modelsLoading by remember{mutableStateOf(false)}
    var modelMenu by remember{mutableStateOf(false)}
    var style by remember{mutableStateOf("سینمایی")}
    var mood by remember{mutableStateOf("احساسی و الهام‌بخش")}
    var instruments by remember{mutableStateOf("پیانو، سازهای زهی، پدهای محیطی")}
    var bpm by remember{mutableStateOf("90")}
    var vocal by remember{mutableStateOf("بی‌کلام")}
    var voiceStyle by remember{mutableStateOf("صدای زنانه، گرم و طبیعی")}
    var lyrics by remember{mutableStateOf("")}
    var extraPrompt by remember{mutableStateOf("")}
    var secondsText by remember{mutableStateOf("60")}
    var temperature by remember{mutableFloatStateOf(0.7f)}
    var seedText by remember{mutableStateOf("")}
    var advanced by remember{mutableStateOf(false)}
    var generating by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var generated by remember{mutableStateOf<NetArzMusicResult?>(null)}
    var player by remember{mutableStateOf<MediaPlayer?>(null)}
    var playing by remember{mutableStateOf(false)}
    var tempFile by remember{mutableStateOf<java.io.File?>(null)}

    fun stopPreview(){
        runCatching{player?.stop()};runCatching{player?.release()}
        player=null;playing=false
    }
    val saveLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")){uri:Uri? ->
        val result=generated
        if(uri!=null&&result!=null)scope.launch{
            runCatching{withContext(Dispatchers.IO){
                context.contentResolver.openOutputStream(uri,"w")?.use{it.write(result.bytes)}
                    ?:error("فایل مقصد برای ذخیره باز نشد.")
            }}.onSuccess{status="فایل موسیقی با موفقیت ذخیره شد."}
                .onFailure{status="ذخیره ناموفق بود: "+(it.message?:"خطای فایل")}
        }
    }
    DisposableEffect(Unit){onDispose{stopPreview()}}
    LaunchedEffect(baseUrl,apiKey){
        if(apiKey.isNotBlank()){
            modelsLoading=true
            runCatching{api.musicModels()}.onSuccess{found->if(found.isNotEmpty())models=found}
                .onFailure{status="فهرست مدل‌ها دریافت نشد؛ از مدل‌های پیش‌فرض استفاده می‌شود."}
            modelsLoading=false
        }
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202630)),shape=RoundedCornerShape(16.dp)){
            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.AutoAwesome,null,tint=Color(0xFFD9C18B))
                    Spacer(Modifier.width(8.dp))
                    Text("استودیوی ساخت موسیقی هوش مصنوعی",color=Color.White,fontWeight=FontWeight.Bold)
                }
                Text("ساخت موسیقی و جلوه صوتی با API پولی نت‌ارز؛ مستقل از ویدیوساز رایگان.",color=Color(0xFFB9C2CE),style=MaterialTheme.typography.bodySmall)
                Text(if(apiKey.isBlank())"اتصال API تنظیم نشده؛ ابتدا کلید و آدرس سرور را در تنظیمات وارد کن." else "اتصال API شناسایی شد • کلید فقط به‌صورت امن از تنظیمات خوانده می‌شود.",color=if(apiKey.isBlank())Color(0xFFFFB4A9) else Color(0xFFB9E5C9),style=MaterialTheme.typography.labelSmall)
            }
        }
        Text("۱. مدل تولید",color=Color.White,fontWeight=FontWeight.Bold)
        Box{
            OutlinedButton(onClick={modelMenu=true},modifier=Modifier.fillMaxWidth()){
                Text(model,modifier=Modifier.weight(1f),color=Color.White)
                Icon(Icons.Default.ArrowDropDown,null)
            }
            DropdownMenu(expanded=modelMenu,onDismissRequest={modelMenu=false}){
                models.forEach{item->DropdownMenuItem(text={Text(item)},onClick={model=item;config.saveModelFor("music",item);modelMenu=false})}
            }
        }
        if(modelsLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(when{
            model.contains("clip") -> "کلیپ کوتاه، مناسب جینگل، اینترو و لوپ (حدود ۳۰ ثانیه)"
            model=="lyria-3.5"||model.contains("pro-preview") -> "آهنگ کامل با امکان توصیف سبک و شعر"
            model=="eleven-music-v1" -> "موسیقی با طول قابل انتخاب از ۳ تا ۳۰۰ ثانیه"
            else -> "ساخت جلوه صوتی؛ طول قابل انتخاب از نیم تا ۳۰ ثانیه"
        },color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)

        Text("۲. مشخصات قطعه",color=Color.White,fontWeight=FontWeight.Bold)
        Text("سبک موسیقی",color=Color(0xFFBFC7D2),style=MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("سینمایی","پاپ","الکترونیک","لوفای","جاز","کلاسیک","راک","Ambient","موسیقی سنتی").forEach{
                FilterChip(selected=style==it,onClick={style=it},label={Text(it)})
            }
        }
        OutlinedTextField(value=mood,onValueChange={mood=it},modifier=Modifier.fillMaxWidth(),label={Text("حال‌وهوا")},placeholder={Text("مثلاً باشکوه، مرموز، آرام، پرانرژی")},singleLine=true)
        OutlinedTextField(value=instruments,onValueChange={instruments=it},modifier=Modifier.fillMaxWidth(),label={Text("سازها و بافت صوتی")},placeholder={Text("پیانو، ویولن، سینتی‌سایزر، درام…")})
        OutlinedTextField(value=bpm,onValueChange={bpm=it.filter(Char::isDigit).take(3)},modifier=Modifier.fillMaxWidth(),label={Text("سرعت قطعه (BPM)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Text("نوع قطعه",color=Color(0xFFBFC7D2),style=MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("بی‌کلام","با کلام","جلوه صوتی").forEach{FilterChip(selected=vocal==it,onClick={vocal=it},label={Text(it)})}
        }
        if(vocal=="با کلام"){
            OutlinedTextField(value=voiceStyle,onValueChange={voiceStyle=it},modifier=Modifier.fillMaxWidth(),label={Text("نوع و حس خواننده")},singleLine=true)
            OutlinedTextField(value=lyrics,onValueChange={lyrics=it},modifier=Modifier.fillMaxWidth(),minLines=4,label={Text("متن ترانه (اختیاری)")},placeholder={Text("[Verse]\nمتن بند اول…\n[Chorus]\nترجیع‌بند…")})
            Text("برای آواز فارسی، تلفظ و وزن شعر ممکن است نیاز به چند بار تولید و انتخاب بهترین خروجی داشته باشد.",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value=extraPrompt,onValueChange={extraPrompt=it},modifier=Modifier.fillMaxWidth(),minLines=3,label={Text("توضیحات تکمیلی")},placeholder={Text("مثلاً شروع خلوت، اوج تدریجی در میانه و پایان آرام؛ مناسب ویدیوی معماری لوکس…")})
        if(model.startsWith("eleven-")){
            OutlinedTextField(value=secondsText,onValueChange={secondsText=it.filter(Char::isDigit).take(3)},modifier=Modifier.fillMaxWidth(),label={Text(if(vocal=="جلوه صوتی")"مدت جلوه صوتی (۰٫۵ تا ۳۰ ثانیه)" else "مدت موسیقی (۳ تا ۳۰۰ ثانیه)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        TextButton(onClick={advanced=!advanced}){Icon(Icons.Default.Tune,null);Text(if(advanced)"بستن تنظیمات پیشرفته" else "تنظیمات پیشرفته")}
        if(advanced){
            if(model.startsWith("openrouter/")){
                Text("تنوع خلاقانه: ${"%.1f".format(java.util.Locale.US,temperature)}",color=Color.White)
                Slider(value=temperature,onValueChange={temperature=it},valueRange=0f..1f)
                OutlinedTextField(value=seedText,onValueChange={seedText=it.filter{ch->ch.isDigit()||ch=='-'}.take(18)},modifier=Modifier.fillMaxWidth(),label={Text("Seed (اختیاری برای خروجی نزدیک‌تر)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }else Text("پارامترهای تنوع و Seed فقط برای مدل‌های OpenRouter اعمال می‌شوند.",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.bodySmall)
        }
        Text("هزینه: مدل‌های Lyria به‌ازای هر قطعه و مدل‌های ElevenLabs بر اساس مدت انتخابی محاسبه می‌شوند. قبل از تولید، موجودی حساب نت‌ارز را بررسی کن؛ قیمت نهایی به مدل وابسته است.",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
        Button(
            onClick={
                val duration=secondsText.toDoubleOrNull()
                if(model.startsWith("eleven-")){
                    val min=if(model.contains("sound-effects"))0.5 else 3.0
                    val max=if(model.contains("sound-effects"))30.0 else 300.0
                    if(duration==null||duration !in min..max){status="مدت انتخابی خارج از محدوده مجاز این مدل است.";return@Button}
                }
                val prompt=buildString{
                    append("Create a ${if(vocal=="جلوه صوتی")"sound effect" else "music track"} in $style style. ")
                    append("Mood: $mood. Instruments and sound design: $instruments. ")
                    if(bpm.isNotBlank())append("Tempo: $bpm BPM. ")
                    if(vocal=="بی‌کلام")append("Instrumental only, no vocals, no lyrics. ")
                    if(vocal=="با کلام"){
                        append("Vocals: $voiceStyle. Sing the supplied lyrics. ")
                        if(lyrics.isNotBlank())append("Lyrics:\n$lyrics\n")
                    }
                    if(vocal=="جلوه صوتی")append("Generate only the sound effect, no music bed. ")
                    if(extraPrompt.isNotBlank())append("Additional direction: $extraPrompt")
                }.take(4000)
                generating=true;status="در حال ارسال درخواست به نت‌ارز…";generated=null;stopPreview()
                scope.launch{
                    runCatching{
                        api.generateMusic(
                            model=model,
                            prompt=prompt,
                            seconds=if(model.startsWith("eleven-"))duration else null,
                            temperature=if(model.startsWith("openrouter/"))temperature.toDouble() else null,
                            seed=if(model.startsWith("openrouter/"))seedText.toLongOrNull() else null
                        )
                    }.onSuccess{result->
                        generated=result
                        config.saveModelFor("music",model)
                        val file=java.io.File(context.cacheDir,"supra_music_${System.currentTimeMillis()}.${result.format}")
                        withContext(Dispatchers.IO){file.writeBytes(result.bytes)}
                        tempFile=file
                        status="ساخت موسیقی کامل شد • ${result.bytes.size/1024} KB • ${result.format.uppercase()}"
                        onMessage("فایل موسیقی جدید آماده شد.")
                    }.onFailure{err->status=err.message?:"ساخت موسیقی ناموفق بود."}
                    generating=false
                }
            },
            enabled=!generating&&apiKey.isNotBlank()&&extraPrompt.length+lyrics.length<3900,
            modifier=Modifier.fillMaxWidth()
        ){
            if(generating){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("در حال ساخت…")}
            else{Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("ساخت موسیقی با API پولی")}
        }
        if(generating)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank())Text(status,color=if(status.contains("ناموفق")||status.contains("خارج"))Color(0xFFFFB4A9) else Color(0xFFB9E5C9),style=MaterialTheme.typography.bodySmall)
        generated?.let{result->
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF202630)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("خروجی آماده است",color=Color.White,fontWeight=FontWeight.Bold)
                    Text("مدل: ${result.model} • حجم: ${result.bytes.size/1024} KB"+(result.seconds?.let{" • مدت: ${it.toInt()} ثانیه"}.orEmpty()),color=Color(0xFFBFC7D2),style=MaterialTheme.typography.bodySmall)
                    if(result.text.isNotBlank())Text("متن/توضیح برگشتی مدل: ${result.text}",color=Color(0xFFD4D9E0),style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={
                            val file=tempFile
                            if(file==null||!file.exists()){status="فایل موقت پخش پیدا نشد؛ دوباره تولید کن.";return@OutlinedButton}
                            stopPreview()
                            val mp=MediaPlayer()
                            player=mp
                            mp.setOnPreparedListener{it.start();playing=true}
                            mp.setOnCompletionListener{playing=false}
                            mp.setOnErrorListener{_,_,_->status="پخش فایل با خطا روبه‌رو شد.";playing=false;true}
                            runCatching{mp.setDataSource(file.absolutePath);mp.prepareAsync()}.onFailure{status=it.message?:"پخش ناموفق بود."}
                        },modifier=Modifier.weight(1f)){Icon(Icons.Default.PlayArrow,null);Text("پخش")}
                        OutlinedButton(onClick={stopPreview()},modifier=Modifier.weight(1f)){Icon(Icons.Default.Stop,null);Text("توقف")}
                    }
                    Button(onClick={saveLauncher.launch("Supra-Music-${System.currentTimeMillis()}.${result.format}")},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Download,null);Text("ذخیره فایل ${result.format.uppercase()}")
                    }
                    if(result.requestId.isNotBlank())Text("شناسه درخواست: ${result.requestId}",color=Color(0xFF8D96A4),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
