package com.naeem.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import android.text.Html
import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

private data class PodcastFeed(val title:String,val category:String,val language:String,val url:String,val publisher:String)
private data class PodcastEpisode(
    val id:String,val title:String,val description:String,val audioUrl:String,
    val published:String,val duration:String,val showTitle:String
)

private val freePodcastFeeds = listOf(
    PodcastFeed("Global News Podcast","خبر و جهان","انگلیسی","https://podcasts.files.bbci.co.uk/p02nq0gn.rss","BBC"),
    PodcastFeed("6 Minute English","یادگیری زبان","انگلیسی","https://podcasts.files.bbci.co.uk/p02pc9tn.rss","BBC"),
    PodcastFeed("The Inquiry","تحلیل و مستند","انگلیسی","https://podcasts.files.bbci.co.uk/p029399x.rss","BBC"),
    PodcastFeed("The Infinite Monkey Cage","علم و فناوری","انگلیسی","https://podcasts.files.bbci.co.uk/b00snr0w.rss","BBC"),
    PodcastFeed("NASA JPL Podcasts","فضا و علم","انگلیسی","https://www.jpl.nasa.gov/feeds/podcasts/","NASA JPL"),
    PodcastFeed("رادیو راه","روان‌شناسی و رشد فردی","فارسی","https://feed.podbean.com/RadioRaah/feed.xml","Radio Raah"),
    PodcastFeed("راه گوش","موسیقی و فرهنگ","فارسی","https://feeds.acast.com/public/shows/627aaa14f8074c0012c28528","Acast")
)

private class PodcastFeedReader {
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
    suspend fun load(feed:PodcastFeed):List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val request=Request.Builder().url(feed.url).header("User-Agent","EMA-ASI-PLATFORM-Podcast/1.0").build()
        http.newCall(request).execute().use { response ->
            if(!response.isSuccessful) error("فید پاسخ نداد: HTTP \${response.code}")
            val xml=response.body?.string().orEmpty()
            if(xml.isBlank()) error("فید خالی است.")
            parse(xml,feed.title).take(60)
        }
    }
    private fun parse(xml:String,fallbackTitle:String):List<PodcastEpisode> {
        val parser=Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
        parser.setInput(StringReader(xml))
        val episodes=mutableListOf<PodcastEpisode>()
        var show=fallbackTitle
        var inItem=false
        var title="";var description="";var audio="";var date="";var duration=""
        var event=parser.eventType
        while(event!=XmlPullParser.END_DOCUMENT){
            when(event){
                XmlPullParser.START_TAG -> {
                    val tag=parser.name.substringAfter(':').lowercase()
                    if(tag=="item"||tag=="entry"){
                        inItem=true;title="";description="";audio="";date="";duration=""
                    } else if(!inItem && tag=="title" && show==fallbackTitle){
                        show=readText(parser).ifBlank{fallbackTitle}
                    } else if(inItem){
                        when(tag){
                            "title" -> title=readText(parser)
                            "description","summary","encoded" -> if(description.isBlank()) description=readText(parser)
                            "pubdate","published","updated" -> date=readText(parser)
                            "duration" -> duration=readText(parser)
                            "enclosure","content" -> {
                                val type=parser.getAttributeValue(null,"type").orEmpty()
                                val url=parser.getAttributeValue(null,"url").orEmpty()
                                if(audio.isBlank() && (type.startsWith("audio") || url.endsWith(".mp3",true) || url.contains("audio"))) audio=url
                            }
                            "link" -> {
                                val href=parser.getAttributeValue(null,"href").orEmpty()
                                val rel=parser.getAttributeValue(null,"rel").orEmpty()
                                val type=parser.getAttributeValue(null,"type").orEmpty()
                                if(audio.isBlank() && (type.startsWith("audio") || rel=="enclosure")) audio=href
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag=parser.name.substringAfter(':').lowercase()
                    if((tag=="item"||tag=="entry") && inItem){
                        val cleanTitle=title.trim()
                        if(cleanTitle.isNotBlank() && audio.startsWith("http")){
                            val cleanDescription=Html.fromHtml(description,Html.FROM_HTML_MODE_COMPACT).toString().trim()
                            episodes.add(PodcastEpisode(audio,cleanTitle,cleanDescription,audio,date,duration,show))
                        }
                        inItem=false
                    }
                }
            }
            event=parser.next()
        }
        return episodes
    }
    private fun readText(parser:XmlPullParser):String {
        val depth=parser.depth
        val value=StringBuilder()
        var event=parser.next()
        while(!(event==XmlPullParser.END_TAG && parser.depth==depth)){
            if(event==XmlPullParser.TEXT||event==XmlPullParser.CDSECT)value.append(parser.text)
            event=parser.next()
        }
        return value.toString().trim()
    }
}

private class PodcastPrefs(context:android.content.Context){
    private val prefs=context.getSharedPreferences("supra_podcasts",android.content.Context.MODE_PRIVATE)
    fun saved()=prefs.getStringSet("saved",emptySet())?.toSet()?:emptySet()
    fun savedEpisodes():List<PodcastEpisode>{
        val arr=runCatching{org.json.JSONArray(prefs.getString("saved_episodes","[]"))}.getOrElse{org.json.JSONArray()}
        return (0 until arr.length()).mapNotNull{runCatching{
            val o=arr.getJSONObject(it)
            PodcastEpisode(o.getString("id"),o.getString("title"),o.optString("description"),o.getString("audioUrl"),o.optString("published"),o.optString("duration"),o.optString("showTitle"))
        }.getOrNull()}
    }
    fun toggle(ep:PodcastEpisode):Pair<Set<String>,List<PodcastEpisode>>{
        val next=saved().toMutableSet()
        val items=savedEpisodes().toMutableList()
        if(!next.add(ep.id)){next.remove(ep.id);items.removeAll{it.id==ep.id}}
        else items.removeAll{it.id==ep.id}.also{items.add(0,ep)}
        val arr=org.json.JSONArray()
        items.forEach{o->arr.put(org.json.JSONObject().put("id",o.id).put("title",o.title).put("description",o.description).put("audioUrl",o.audioUrl).put("published",o.published).put("duration",o.duration).put("showTitle",o.showTitle))}
        prefs.edit().putStringSet("saved",next).putString("saved_episodes",arr.toString()).apply()
        return next to items
    }
    fun customFeeds():List<PodcastFeed>{
        val raw=prefs.getString("custom_feeds","[]").orEmpty()
        val arr=runCatching{org.json.JSONArray(raw)}.getOrElse{org.json.JSONArray()}
        return (0 until arr.length()).mapNotNull{runCatching{
            val o=arr.getJSONObject(it);PodcastFeed(o.getString("title"),"شخصی","نامشخص",o.getString("url"),"RSS")
        }.getOrNull()}
    }
    fun addFeed(title:String,url:String):List<PodcastFeed>{
        val all=customFeeds().toMutableList()
        if(all.none{it.url==url})all.add(PodcastFeed(title,"شخصی","نامشخص",url,"RSS"))
        val arr=org.json.JSONArray()
        all.forEach{arr.put(org.json.JSONObject().put("title",it.title).put("url",it.url))}
        prefs.edit().putString("custom_feeds",arr.toString()).apply()
        return all
    }
}

@Composable
fun PodcastDashboard(onClose:()->Unit){
    val context=LocalContext.current
    val reader= remember{PodcastFeedReader()}
    val prefs= remember{PodcastPrefs(context.applicationContext)}
    val scope= rememberCoroutineScope()
    var customFeeds by remember{mutableStateOf(prefs.customFeeds())}
    val feeds=freePodcastFeeds+customFeeds
    var selectedFeed by remember{mutableStateOf(freePodcastFeeds.first())}
    var episodes by remember{mutableStateOf<List<PodcastEpisode>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf(prefs.saved())}
    var savedItems by remember{mutableStateOf(prefs.savedEpisodes())}
    var savedOnly by remember{mutableStateOf(false)}
    var search by remember{mutableStateOf("")}
    var addFeedDialog by remember{mutableStateOf(false)}
    var feedTitle by remember{mutableStateOf("")}
    var feedUrl by remember{mutableStateOf("")}
    var playerTitle by remember{mutableStateOf("")}
    var current by remember{mutableStateOf<PodcastEpisode?>(null)}
    var playing by remember{mutableStateOf(false)}
    var prepared by remember{mutableStateOf(false)}
    var position by remember{mutableIntStateOf(0)}
    var duration by remember{mutableIntStateOf(0)}
    var playError by remember{mutableStateOf("")}
    var seekRequested by remember{mutableStateOf(false)}
    val bg=Color.Transparent;val panel=SupraGlass.Glass;val accent=SupraGlass.Ice;val muted=SupraGlass.Muted

    fun sendPlaybackAction(action:String, episode:PodcastEpisode?=null, seek:Int?=null) {
        val intent=Intent(context,PodcastPlaybackService::class.java).setAction(action)
        episode?.let {
            intent.putExtra(PodcastPlaybackService.EXTRA_URL,it.audioUrl)
            intent.putExtra(PodcastPlaybackService.EXTRA_TITLE,it.title)
            intent.putExtra(PodcastPlaybackService.EXTRA_SHOW,it.showTitle)
        }
        seek?.let { intent.putExtra(PodcastPlaybackService.EXTRA_POSITION,it) }
        if(action==PodcastPlaybackService.ACTION_PLAY) ContextCompat.startForegroundService(context,intent)
        else context.startService(intent)
    }
    fun stopPlayer(){
        sendPlaybackAction(PodcastPlaybackService.ACTION_STOP)
        current=null;playing=false;prepared=false;position=0;duration=0;playerTitle=""
    }
    fun playEpisode(ep:PodcastEpisode){
        playError=""
        current=ep;playerTitle=ep.title;position=0;duration=0;prepared=false;playing=false
        sendPlaybackAction(PodcastPlaybackService.ACTION_PLAY,ep)
    }
    DisposableEffect(Unit){
        val receiver=object:BroadcastReceiver(){
            override fun onReceive(ctx:Context?,intent:Intent?){
                intent ?: return
                val errorMessage=intent.getStringExtra("error")
                if(!errorMessage.isNullOrBlank()){playError=errorMessage;prepared=false;playing=false;return}
                val url=intent.getStringExtra(PodcastPlaybackService.EXTRA_URL).orEmpty()
                if(url.isNotBlank() && current?.audioUrl!=url){
                    current=PodcastEpisode(url,intent.getStringExtra(PodcastPlaybackService.EXTRA_TITLE).orEmpty(),"",url,"","",intent.getStringExtra(PodcastPlaybackService.EXTRA_SHOW).orEmpty())
                }
                position=intent.getIntExtra(PodcastPlaybackService.EXTRA_POSITION,0)
                duration=intent.getIntExtra("duration",0)
                playing=intent.getBooleanExtra("playing",false)
                prepared=intent.getBooleanExtra("prepared",false)
            }
        }
        val filter=IntentFilter(PodcastPlaybackService.ACTION_STATUS)
        if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver,filter)
        onDispose{runCatching{context.unregisterReceiver(receiver)}}
    }
    fun loadFeed(feed:PodcastFeed){
        selectedFeed=feed;loading=true;error="";episodes=emptyList();savedOnly=false
        scope.launch{
            try{episodes=reader.load(feed);if(episodes.isEmpty())error="در این فید قسمت صوتی قابل پخشی پیدا نشد."}
            catch(e:Exception){error=e.message?:"دریافت پادکست ناموفق بود."}
            finally{loading=false}
        }
    }
    LaunchedEffect(Unit){loadFeed(selectedFeed)}
    val sourceEpisodes=if(savedOnly)savedItems else episodes
    val visible=sourceEpisodes.filter{search.isBlank()||it.title.contains(search,true)||it.description.contains(search,true)}
    Column(Modifier.fillMaxSize().background(bg).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("ایما / پادکست",color=muted,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text("پادکست",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("فیدهای عمومی رایگان • پخش مستقیم داخل ایما",color=muted,style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={onClose}){Icon(Icons.Default.Close,null,tint=accent)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
            FilterChip(selected=!savedOnly,onClick={savedOnly=false},label={Text("کشف پادکست")})
            FilterChip(selected=savedOnly,onClick={savedOnly=true},label={Text("بعداً گوش می‌دهم")})
            Spacer(Modifier.weight(1f))
            IconButton(onClick={addFeedDialog=true}){Icon(Icons.Default.AddLink,"افزودن RSS",tint=accent)}
            IconButton(onClick={loadFeed(selectedFeed)}){Icon(Icons.Default.Refresh,"بروزرسانی",tint=accent)}
        }
        if(!savedOnly){
            Text("موضوع‌ها",color=muted,style=MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                items(feeds,key={it.url}){feed->
                    FilterChip(selected=selectedFeed.url==feed.url,onClick={loadFeed(feed)},label={Text(feed.title,maxLines=1,overflow=TextOverflow.Ellipsis)})
                }
            }
            Text(selectedFeed.category+"  •  "+selectedFeed.publisher+"  •  "+selectedFeed.language,color=muted,style=MaterialTheme.typography.labelSmall)
        }
        OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجو در قسمت‌ها")},leadingIcon={Icon(Icons.Default.Search,null)})
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=accent)
        if(error.isNotBlank())Text(error,color=Color(0xFFFFB4A9),style=MaterialTheme.typography.bodySmall)
        if(visible.isEmpty()&&!loading&&error.isBlank())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("قسمتی برای نمایش نیست.",color=muted)}
        else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=10.dp)){
            items(visible,key={it.id}){ep->
                Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(15.dp)){
                    Row(Modifier.fillMaxWidth().clickable{playEpisode(ep)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Podcasts,null,tint=accent,modifier=Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(ep.showTitle+"  •  "+ep.published+"  •  "+ep.duration,color=muted,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(ep.description.isNotBlank())Text(ep.description,color=accent.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        IconButton(onClick={val updated=prefs.toggle(ep);saved=updated.first;savedItems=updated.second}){Icon(if(ep.id in saved)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,null,tint=accent)}
                    }
                }
            }
        }
        if(current!=null){
            Card(colors=CardDefaults.cardColors(containerColor=SupraGlass.Glass),shape=RoundedCornerShape(17.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("در حال پخش",color=muted,style=MaterialTheme.typography.labelSmall)
                    Text(current!!.title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=2)
                    if(playError.isNotBlank())Text(playError,color=Color(0xFFFFB4A9),style=MaterialTheme.typography.bodySmall)
                    Slider(value=if(duration>0)position.toFloat().coerceIn(0f,duration.toFloat()) else 0f,onValueChange={position=it.toInt()},onValueChangeFinished={sendPlaybackAction(PodcastPlaybackService.ACTION_SEEK,seek=position)},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=prepared)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("%02d:%02d".format(position/60000,(position/1000)%60),color=muted,style=MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(enabled=prepared,onClick={sendPlaybackAction(PodcastPlaybackService.ACTION_SEEK,seek=(position-15000).coerceAtLeast(0))}){Icon(Icons.Default.Replay,null,tint=accent)}
                        IconButton(enabled=prepared,onClick={
                            sendPlaybackAction(PodcastPlaybackService.ACTION_TOGGLE)
                        }){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(30.dp))}
                        IconButton(enabled=prepared,onClick={sendPlaybackAction(PodcastPlaybackService.ACTION_SEEK,seek=(position+15000).coerceAtMost(duration))}){Icon(Icons.Default.FastForward,null,tint=accent)}
                        IconButton(onClick={stopPlayer()}){Icon(Icons.Default.Stop,null,tint=accent)}
                        Spacer(Modifier.weight(1f))
                        Text("%02d:%02d".format(duration/60000,(duration/1000)%60),color=muted,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if(addFeedDialog){
        AlertDialog(onDismissRequest={addFeedDialog=false},containerColor=panel,title={Text("افزودن پادکست رایگان",color=Color.White)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("نشانی فید عمومی RSS را وارد کن. فقط فیدهایی که فایل صوتی مستقیم دارند قابل پخش‌اند.",color=muted)
                OutlinedTextField(value=feedTitle,onValueChange={feedTitle=it},label={Text("نام پادکست")},singleLine=true)
                OutlinedTextField(value=feedUrl,onValueChange={feedUrl=it},label={Text("آدرس RSS")},singleLine=true)
            }},
            confirmButton={TextButton(onClick={
                if(feedTitle.isNotBlank()&&feedUrl.startsWith("https://")){
                    customFeeds=prefs.addFeed(feedTitle.trim(),feedUrl.trim())
                    val newFeed=customFeeds.last();feedTitle="";feedUrl="";addFeedDialog=false;loadFeed(newFeed)
                }
            }){Text("افزودن")}},
            dismissButton={TextButton(onClick={addFeedDialog=false}){Text("انصراف")}})
    }
}
