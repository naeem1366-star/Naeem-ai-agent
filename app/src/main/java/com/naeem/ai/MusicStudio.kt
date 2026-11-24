package com.naeem.ai

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
            listOf("کشف آهنگ","میز دی‌جی","آلبوم‌ها").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}
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
