package com.naeem.ai

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val FG=Color(0xFFD7B77D)
private val FB=Color(0xFF111318)
private val FC=Color(0xFF20232B)
private val FM=Color(0xFFA8ADB8)

@Composable
fun FeatureHub(
    onClose:()->Unit,onFinance:()->Unit,onHealth:()->Unit,onGames:()->Unit,
    onDiscover:()->Unit,onMusic:()->Unit,onMemory:()->Unit,onAlbum:()->Unit,
    onSettings:()->Unit,onTravel:()->Unit
){
    var query by remember { mutableStateOf("") }
    val listState= rememberLazyListState()
    data class Entry(val title:String,val subtitle:String,val detail:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val color:Color,val action:()->Unit,val keywords:String)
    val entries=listOf(
        Entry("سفر و هم‌سفرها","برنامه‌ریزی سفر","ساخت سفر، افزودن همراهان و ثبت مخارج",Icons.Default.Luggage,Color(0xFF73D2D0),onTravel,"سفر مسافرت مقصد همسفر هزینه"),
        Entry("دفتر مالی","مدیریت پول","ثبت درآمد و هزینه و گزارش مالی",Icons.Default.AccountBalanceWallet,Color(0xFFFFC477),onFinance,"مالی پول هزینه درآمد"),
        Entry("سلامت و تغذیه","بدن و انرژی","تغذیه، کالری و فعالیت روزانه",Icons.Default.Favorite,Color(0xFF8CE0B5),onHealth,"سلامت تغذیه کالری غذا"),
        Entry("حافظه هوشمند","یادداشت‌های من","نگهداری نکته‌ها و اطلاعات مهم",Icons.Default.Bookmark,Color(0xFFB9A4FF),onMemory,"حافظه یادداشت"),
        Entry("آلبوم تصاویر","آرشیو آثار","تصاویر ساخته‌شده با هوش مصنوعی",Icons.Default.Collections,Color(0xFF8CCCF1),onAlbum,"آلبوم تصویر عکس"),
        Entry("بازی‌ها","چالش و سرگرمی","بازی‌های کوتاه و گروهی",Icons.Default.SportsEsports,Color(0xFFB5A2FF),onGames,"بازی سرگرمی"),
        Entry("سینما","فیلم و پوستر","کشف فیلم و پیشنهاد تماشا",Icons.Default.Movie,Color(0xFFFFB987),onDiscover,"فیلم سینما"),
        Entry("موسیقی","آهنگ و هنرمند","جست‌وجو و شنیدن پیش‌نمایش آهنگ",Icons.Default.MusicNote,Color(0xFF83CFF0),onMusic,"موسیقی آهنگ"),
        Entry("تنظیمات","اتصال و حریم خصوصی","مدیریت اتصال هوش مصنوعی",Icons.Default.Settings,Color(0xFFD1D5DD),onSettings,"تنظیمات اتصال")
    )
    val filtered=entries.filter{query.isBlank()||it.title.contains(query,true)||it.subtitle.contains(query,true)||it.keywords.contains(query,true)}
    Column(Modifier.fillMaxSize().background(Color(0xFF0C1118)).padding(horizontal=18.dp, vertical=12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("NAEEM AI  /  SPACES",color=Color(0xFF8CE0B5),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text("جهان ابزارهای تو",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("برای ورود، روی هر فضا بزن.",color=Color(0xFFA9B8B2),style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Color(0xFF8CE0B5))}
        }
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF172B2A)),shape=RoundedCornerShape(24.dp)){
            Box(Modifier.fillMaxWidth().background(ComposeBrush.linearGradient(listOf(Color(0xFF28594C),Color(0xFF1B3440),Color(0xFF25243A)))).padding(17.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text("SCROLL TO EXPLORE",color=Color(0xFFB5F0D0),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                        Text("هر قابلیت، یک فضای مستقل",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text("با اسکرول، فضاها در عمق صحنه حرکت می‌کنند.",color=Color(0xFFD0DED8),style=MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(62.dp).background(ComposeBrush.radialGradient(listOf(Color(0xFF9CE8C0),Color(0xFF376C58),Color.Transparent)),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){
                        Icon(Icons.Default.ViewInAr,null,tint=Color(0xFFD5FFE5),modifier=Modifier.size(34.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp),placeholder={Text("جست‌وجو در فضاها…")},leadingIcon={Icon(Icons.Default.Search,null,tint=Color(0xFF8CE0B5))},colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color(0xFF8CE0B5),unfocusedBorderColor=Color(0xFF394640),focusedContainerColor=Color(0xFF151F24),unfocusedContainerColor=Color(0xFF151F24)))
        Spacer(Modifier.height(8.dp))
        LazyColumn(state=listState,modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(13.dp),contentPadding=PaddingValues(top=8.dp,bottom=28.dp)){
            itemsIndexed(filtered,key={_,entry->entry.title}){index,entry->
                val relative=index-listState.firstVisibleItemIndex-listState.firstVisibleItemScrollOffset/520f
                Card(
                    onClick=entry.action,
                    modifier=Modifier.fillMaxWidth().height(150.dp).graphicsLayer{
                        val depth=relative.coerceIn(-1.4f,1.4f)
                        rotationX=depth * -6.5f
                        translationZ=-kotlin.math.abs(depth)*42f
                        scaleX=1f-kotlin.math.abs(depth)*0.045f
                        scaleY=1f-kotlin.math.abs(depth)*0.045f
                        alpha=(1f-kotlin.math.abs(depth)*0.16f).coerceIn(0.72f,1f)
                        cameraDistance=24f*density
                    },
                    colors=CardDefaults.cardColors(containerColor=Color(0xFF19232B)),
                    shape=RoundedCornerShape(23.dp),
                    elevation=CardDefaults.cardElevation(defaultElevation=8.dp)
                ){
                    Box(Modifier.fillMaxSize().background(ComposeBrush.linearGradient(listOf(entry.color.copy(alpha=.25f),Color(0xFF19232B),Color(0xFF111820)))).padding(17.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxSize()){
                            Box(Modifier.size(58.dp).background(entry.color.copy(alpha=.17f),RoundedCornerShape(19.dp)),contentAlignment=Alignment.Center){
                                Icon(entry.icon,null,tint=entry.color,modifier=Modifier.size(31.dp))
                            }
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(entry.title,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text(entry.subtitle,color=entry.color,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                                Text(entry.detail,color=Color(0xFFB0BCB8),style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ArrowOutward,null,tint=entry.color,modifier=Modifier.size(21.dp))
                        }
                    }
                }
            }
            if(filtered.isEmpty()) item { Text("قابلیتی با این نام پیدا نشد.",color=FM,modifier=Modifier.padding(16.dp)) }
        }
    }
}
@Composable private fun FCard(content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable private fun FInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=FG,unfocusedBorderColor=Color(0xFF454954)))}

private data class HLog(val name:String,val kcal:Int,val type:String,val at:Long,val protein:Double=0.0,val carbs:Double=0.0,val fat:Double=0.0)
private data class FoodInfo(val name:String,val kcal:Double,val protein:Double,val carbs:Double,val fat:Double)
private val foodBank=listOf(
 FoodInfo("سینه مرغ پخته",165.0,31.0,0.0,3.6),FoodInfo("تخم مرغ",155.0,13.0,1.1,11.0),
 FoodInfo("برنج سفید پخته",130.0,2.7,28.2,0.3),FoodInfo("نان سنگک",259.0,9.0,52.0,1.5),
 FoodInfo("ماست کم‌چرب",63.0,5.3,7.0,1.6),FoodInfo("موز",89.0,1.1,22.8,0.3),
 FoodInfo("سیب",52.0,0.3,13.8,0.2),FoodInfo("بادام",579.0,21.2,21.6,49.9),
 FoodInfo("عدس پخته",116.0,9.0,20.1,0.4),FoodInfo("تن ماهی آبکش‌شده",132.0,29.0,0.0,1.0),
 FoodInfo("شیر کم‌چرب",42.0,3.4,5.0,1.0),FoodInfo("سیب‌زمینی آب‌پز",87.0,1.9,20.1,0.1)
)
private class HStore(c:Context){
 private val p=c.getSharedPreferences("naeem_health_local",Context.MODE_PRIVATE)
 fun get(k:String,d:String="")=p.getString(k,d)?:d
 fun put(k:String,v:String){p.edit().putString(k,v).apply()}
 fun logs():List<HLog> = runCatching{val a=org.json.JSONArray(p.getString("logs","[]"));(0 until a.length()).mapNotNull{i->a.optJSONObject(i)?.let{HLog(it.optString("n"),it.optInt("c"),it.optString("t"),it.optLong("a"),it.optDouble("p"),it.optDouble("b"),it.optDouble("f"))}}}.getOrDefault(emptyList())
 fun add(x:HLog){val a=org.json.JSONArray();(logs()+x).takeLast(500).forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at).put("p",it.protein).put("b",it.carbs).put("f",it.fat))};p.edit().putString("logs",a.toString()).apply()}
 fun remove(at:Long){val a=org.json.JSONArray();logs().filterNot{it.at==at}.forEach{a.put(org.json.JSONObject().put("n",it.name).put("c",it.kcal).put("t",it.type).put("a",it.at))};p.edit().putString("logs",a.toString()).apply()}
}

private val HBg=Color(0xFF101815)
private val HPanel=Color(0xFF1B2923)
private val HMint=Color(0xFF8CE0B5)
private val HMuted=Color(0xFFA0B5AA)
@Composable private fun HCard(content:@Composable ColumnScope.()->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(22.dp),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
 }
}
@Composable private fun HInput(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){
 OutlinedTextField(value,change,modifier=modifier,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(15.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=HMint,unfocusedBorderColor=Color(0xFF496456),focusedContainerColor=Color(0xFF17231D),unfocusedContainerColor=Color(0xFF17231D),focusedLabelColor=HMint,cursorColor=HMint))
}
@Composable private fun HMetric(title:String,value:String,sub:String,modifier:Modifier){
 val icon=when(title){"BMI"->Icons.Default.Favorite;"کالری پایه"->Icons.Default.LocalFireDepartment;"هدف کالری"->Icons.Default.Flag;else->Icons.Default.Restaurant}
 Card(modifier,colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(19.dp),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)){
  Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Icon(icon,null,tint=HMint,modifier=Modifier.size(16.dp));Text(title,color=HMuted,style=MaterialTheme.typography.labelSmall)}
   Text(value,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)
   Text(sub,color=HMuted,style=MaterialTheme.typography.labelSmall)
  }
 }
}

@Composable fun HealthDashboard(onClose:()->Unit){
 val ctx=LocalContext.current;val store=remember{HStore(ctx)}
 var height by remember{mutableStateOf(store.get("h"))};var weight by remember{mutableStateOf(store.get("w"))};var age by remember{mutableStateOf(store.get("age"))}
 var sex by remember{mutableStateOf(store.get("sex","مرد"))};var activity by remember{mutableStateOf(store.get("activity","متوسط"))};var goal by remember{mutableStateOf(store.get("goal","حفظ وزن"))}
 var water by remember{mutableIntStateOf(store.get("water","0").toIntOrNull()?:0)}
 var name by remember{mutableStateOf("")};var kcal by remember{mutableStateOf("")};var logs by remember{mutableStateOf(store.logs())}
 var foodSearch by remember{mutableStateOf("")};var grams by remember{mutableStateOf("100")};var selectedFood by remember{mutableStateOf<FoodInfo?>(null)};var meal by remember{mutableStateOf("ناهار")}
 var walkKm by remember{mutableStateOf("2")};var walkMinutes by remember{mutableStateOf("30")}
 val h=height.toDoubleOrNull()?.div(100.0);val w=weight.toDoubleOrNull();val years=age.toIntOrNull()
 val bmi=if(h!=null&&h>0&&w!=null&&w>0)w/(h*h)else null
 val bmr=if(h!=null&&h>0&&w!=null&&w>0&&years!=null&&years>0)10*w+6.25*(h*100)-5*years+(if(sex=="مرد")5 else -161) else null
 val tdee=bmr?.times(when(activity){"کم‌تحرک"->1.2;"سبک"->1.375;"زیاد"->1.725;else->1.55})?.toInt()
 val target=tdee?.let{when(goal){"کاهش وزن"->it-350;"افزایش وزن"->it+300;else->it}}
 val today=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
 val todayLogs=logs.filter{it.at>=today};val eaten=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.kcal};val burned=todayLogs.filter{it.type=="فعالیت"}.sumOf{it.kcal}
 val nf=remember{NumberFormat.getNumberInstance(Locale.US)}
 Column(Modifier.fillMaxSize().background(HBg).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(48.dp).background(Color(0xFF244B3B),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Eco,null,tint=HMint,modifier=Modifier.size(29.dp))}
   Spacer(Modifier.width(11.dp))
   Column(Modifier.weight(1f)){Text("سلامت و تغذیه",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("داشبورد بدن و انرژی",color=HMuted,style=MaterialTheme.typography.bodySmall)}
   IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=HMint)}
  }
  Card(colors=CardDefaults.cardColors(containerColor=HPanel),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
   Box(Modifier.fillMaxWidth().background(ComposeBrush.linearGradient(listOf(Color(0xFF244D3D),Color(0xFF1D3430),Color(0xFF202D2D)))).padding(17.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Text("HEALTH SPACE",color=Color(0xFFA9EAC7),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
      Text("قدم‌به‌قدم، سالم‌تر",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)
      Text("تغذیه، تحرک و حال خوبت را یک‌جا دنبال کن.",color=Color(0xFFC2D8CD),style=MaterialTheme.typography.bodySmall)
     }
     Box(Modifier.size(68.dp).background(ComposeBrush.radialGradient(listOf(Color(0xFF9AE9BD),Color(0xFF326A50),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center){
      Icon(Icons.Default.HealthAndSafety,null,tint=Color(0xFFE0FFEA),modifier=Modifier.size(37.dp))
     }
    }
   }
  }
  Spacer(Modifier.height(12.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{HCard{
    Text("پروفایل بدن",color=HMint,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){HInput("قد cm",height,{height=it.filter(Char::isDigit)},Modifier.weight(1f));HInput("وزن kg",weight,{weight=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f));HInput("سن",age,{age=it.filter(Char::isDigit)},Modifier.weight(1f))}
    Row{listOf("مرد","زن").forEach{FilterChip(selected=sex==it,onClick={sex=it},label={Text(it)})}}
    Text("فعالیت روزانه",color=FM);Row{listOf("کم‌تحرک","سبک","متوسط","زیاد").forEach{FilterChip(selected=activity==it,onClick={activity=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Text("هدف",color=FM);Row{listOf("کاهش وزن","حفظ وزن","افزایش وزن").forEach{FilterChip(selected=goal==it,onClick={goal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
    Button(onClick={store.put("h",height);store.put("w",weight);store.put("age",age);store.put("sex",sex);store.put("activity",activity);store.put("goal",goal)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ذخیرهٔ پروفایل")}
   }}
   item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    HMetric("BMI",bmi?.let{String.format(Locale.US,"%.1f",it)}?:"—",bmi?.let{when{it<18.5->"کمبود وزن";it<25->"محدوده معمول";it<30->"اضافه‌وزن";else->"بالا"}}?:"قد و وزن را وارد کن",Modifier.weight(1f))
    HMetric("کالری پایه",bmr?.toInt()?.toString()?:"—","BMR روزانه",Modifier.weight(1f))
   };Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=7.dp)){HMetric("هدف کالری",target?.toString()?:"—","برآورد روزانه",Modifier.weight(1f));HMetric("مصرف امروز",eaten.toString(),"فعالیت: "+burned+" kcal",Modifier.weight(1f))}
    val pToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.protein};val cToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.carbs};val fToday=todayLogs.filter{it.type.startsWith("غذا")}.sumOf{it.fat}
   Text("درشت‌مغذی‌های امروز  •  پروتئین "+String.format(Locale.US,"%.1f",pToday)+"g  •  کربوهیدرات "+String.format(Locale.US,"%.1f",cToday)+"g  •  چربی "+String.format(Locale.US,"%.1f",fToday)+"g",color=HMint,style=MaterialTheme.typography.labelSmall)
   if(bmi!=null)Text("BMI فقط شاخص غربالگری است، نه تشخیص پزشکی.",color=FM,style=MaterialTheme.typography.labelSmall)
   }
   item{HCard{
    Text("بانک غذایی؛ محاسبه بر اساس گرم",color=HMint,fontWeight=FontWeight.Bold)
    OutlinedTextField(foodSearch,{foodSearch=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("جست‌وجوی ماده غذایی")},leadingIcon={Icon(Icons.Default.Search,null)})
    foodBank.filter{it.name.contains(foodSearch,true)}.forEach{food->TextButton(onClick={selectedFood=food}){Text(food.name+" • "+food.kcal.toInt()+" kcal / ۱۰۰ گرم",color=if(selectedFood==food)HMint else Color.White)}}
    if(selectedFood!=null){
     Text("انتخاب: "+selectedFood!!.name,color=HMint,fontWeight=FontWeight.SemiBold)
     HInput("مقدار به گرم",grams,{grams=it.filter{c->c.isDigit()||c=='.'}},Modifier.fillMaxWidth())
     Row{listOf("صبحانه","ناهار","شام","میان‌وعده").forEach{FilterChip(selected=meal==it,onClick={meal=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}
     val g=grams.toDoubleOrNull()?:0.0;val factor=g/100.0
     Text("انرژی: "+(selectedFood!!.kcal*factor).toInt()+" kcal • پروتئین: "+String.format(Locale.US,"%.1f",selectedFood!!.protein*factor)+" g",color=HMint)
     Text("کربوهیدرات: "+String.format(Locale.US,"%.1f",selectedFood!!.carbs*factor)+" g • چربی: "+String.format(Locale.US,"%.1f",selectedFood!!.fat*factor)+" g",color=FM,style=MaterialTheme.typography.bodySmall)
     Button(onClick={store.add(HLog(selectedFood!!.name,(selectedFood!!.kcal*factor).roundToInt(),"غذا • "+meal,System.currentTimeMillis(),selectedFood!!.protein*factor,selectedFood!!.carbs*factor,selectedFood!!.fat*factor));logs=store.logs();selectedFood=null;grams="100"},enabled=g>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت در دفترچه غذایی")}
    }
   }}
   item{HCard{Text("ثبت خوراکی و کالری",color=HMint,fontWeight=FontWeight.Bold);HInput("نام غذا یا وعده",name,{name=it},Modifier.fillMaxWidth());HInput("کالری kcal",kcal,{kcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth());Button(onClick={val c=kcal.toIntOrNull()?:0;if(name.isNotBlank()&&c>0){store.add(HLog(name,c,"غذا",System.currentTimeMillis()));logs=store.logs();name="";kcal=""}},enabled=name.isNotBlank()&&kcal.toIntOrNull()!=null,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت وعده")};Text("برای خوراکی‌هایی که در بانک نیستند، کالری را می‌توانی دستی ثبت کنی.",color=FM,style=MaterialTheme.typography.labelSmall)}}
   item{HCard{Text("آب روزانه",color=HMint,fontWeight=FontWeight.Bold);Text(water.toString()+" از ۸ لیوان",color=Color.White,style=MaterialTheme.typography.titleLarge);LinearProgressIndicator(progress={(water/8f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=HMint,trackColor=HBg);Row{OutlinedButton(onClick={water=(water-1).coerceAtLeast(0);store.put("water",water.toString())}){Text("−۱")};Button(onClick={water++;store.put("water",water.toString())},colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("+ یک لیوان")}}}}
   item{HCard{
    var actName by remember{mutableStateOf("تمرین ورزشی")};var actKcal by remember{mutableStateOf("")}
    val walkEstimate=((walkKm.toDoubleOrNull()?:0.0)*(w?:0.0)*0.75).toInt()
    Text("فعالیت و پیاده‌روی",color=HMint,fontWeight=FontWeight.Bold)
    Text("برآورد پیاده‌روی بر اساس مسافت و وزن ثبت‌شده",color=FM,style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){HInput("مسافت km",walkKm,{walkKm=it.filter{c->c.isDigit()||c=='.'}},Modifier.weight(1f));HInput("مدت دقیقه",walkMinutes,{walkMinutes=it.filter(Char::isDigit)},Modifier.weight(1f))}
    Text("کالری تخمینی پیاده‌روی: "+walkEstimate+" kcal",color=HMint,fontWeight=FontWeight.Bold)
    Button(onClick={if(walkEstimate>0){store.add(HLog("پیاده‌روی "+walkKm+" کیلومتر • "+walkMinutes+" دقیقه",walkEstimate,"فعالیت",System.currentTimeMillis()));logs=store.logs()}},enabled=walkEstimate>0,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت پیاده‌روی")}
    Divider(color=FB)
    Text("فعالیت دیگر",color=HMint,fontWeight=FontWeight.SemiBold)
    HInput("نوع فعالیت",actName,{actName=it},Modifier.fillMaxWidth());HInput("کالری سوزانده‌شده",actKcal,{actKcal=it.filter(Char::isDigit)},Modifier.fillMaxWidth())
    Button(onClick={val c=actKcal.toIntOrNull()?:0;if(c>0){store.add(HLog(actName,c,"فعالیت",System.currentTimeMillis()));logs=store.logs();actKcal=""}},enabled=actKcal.toIntOrNull()!=null,colors=ButtonDefaults.buttonColors(containerColor=HMint,contentColor=FB)){Text("ثبت فعالیت")}
   }}
   item{HCard{Text("پیشنهاد تغذیه‌ای",color=HMint,fontWeight=FontWeight.Bold);val tips=when(goal){"کاهش وزن"->listOf("در هر وعده پروتئین و سبزیجات داشته باش.","نوشیدنی شیرین را با آب جایگزین کن.","کاهش کالری را تدریجی نگه دار.");"افزایش وزن"->listOf("از میان‌وعده‌های مغذی استفاده کن.","پروتئین را در وعده‌ها پخش کن.","افزایش انرژی را تدریجی انجام بده.");else->listOf("وعده‌ها را متعادل و متنوع نگه دار.","در طول روز آب کافی بنوش.","پروتئین، سبزیجات و غلات کامل را ترکیب کن.")};tips.forEach{Text("• "+it,color=Color.White,style=MaterialTheme.typography.bodySmall)}}}
   item{Text("ثبت‌های امروز",color=HMint,fontWeight=FontWeight.Bold)}
   items(todayLogs.reversed(),key={it.at}){log->Card(colors=CardDefaults.cardColors(containerColor=HPanel)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(log.name,color=Color.White);Text(log.type,color=FM,style=MaterialTheme.typography.labelSmall)};Text(log.kcal.toString()+" kcal",color=HMint);IconButton(onClick={store.remove(log.at);logs=store.logs()}){Icon(Icons.Default.Delete,null,tint=Color(0xFFFF9A91))}}}}
  }
 }
}
@Composable private fun FMetric(title:String,value:String,sub:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=FC),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(title,color=FM,style=MaterialTheme.typography.labelSmall);Text(value,color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(sub,color=FM,style=MaterialTheme.typography.labelSmall)}}}

@Composable fun GamesHub(onClose:()->Unit){
 var seconds by remember{mutableIntStateOf(60)}
 var running by remember{mutableStateOf(false)}
 var team by remember{mutableIntStateOf(1)}
 var team1 by remember{mutableStateOf("تیم آفتاب")}
 var team2 by remember{mutableStateOf("تیم ماه")}
 var score1 by remember{mutableIntStateOf(0)}
 var score2 by remember{mutableIntStateOf(0)}
 var category by remember{mutableStateOf("ترکیبی")}
 var word by remember{mutableStateOf("برای شروع، کلمه را دریافت کن")}
 var round by remember{mutableIntStateOf(1)}
 var showWord by remember{mutableStateOf(false)}
 val decks=remember{mapOf("ترکیبی" to listOf("فضانورد","چتر نجات","آشپز","ترافیک","کارآگاه","کتابخانه","آتش‌نشان","ماهیگیری","ربات","سینما","دوچرخه","آبشار","جادوگر","بستنی","قطار","عکاس","دندان‌پزشک","کوه‌نوردی","بادبادک","خواب‌آلود"),"سینما" to listOf("کارگردان","فرش قرمز","اسکار","دوربین","فیلمنامه","اکران","جلوه‌های ویژه","بازیگر","سکانس"),"سخت" to listOf("پارادوکس","ناخودآگاه","جاذبه","معماری","دیپلماسی","فرسایش","کهکشان","نوستالژی","الگوریتم","توربین"))}
 LaunchedEffect(running,seconds){if(running&&seconds>0){kotlinx.coroutines.delay(1000);seconds=(seconds-1).coerceAtLeast(0);if(seconds<=1)running=false}}
 fun next(){word=decks[category].orEmpty().random();seconds=60;running=false;showWord=true;round++}
 Column(Modifier.fillMaxSize().background(Color(0xFF0B1020))){
  Row(Modifier.fillMaxWidth().background(Color(0xFF121A30)).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("GAME STUDIO",color=Color(0xFF91A8FF),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("باشگاه بازی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("بازی رو شروع کن؛ امتیازها با ما",color=Color(0xFFADB8D0),style=MaterialTheme.typography.bodySmall)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Color.White)}}
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182541)),shape=RoundedCornerShape(26.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("دور " + round,color=Color(0xFF9EAEFF),fontWeight=FontWeight.Bold);Text("پانتومیم تیمی",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black)};Surface(color=Color(0xFF2C3D66),shape=CircleShape){Text("⏱",modifier=Modifier.padding(12.dp),color=Color.White)}}
    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("ترکیبی","سینما","سخت").forEach{FilterChip(selected=category==it,onClick={category=it},label={Text(it)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF596FEA),selectedLabelColor=Color.White,containerColor=Color(0xFF202A43),labelColor=Color.White))}}
    Box(Modifier.fillMaxWidth().background(Color(0xFF0D1428),RoundedCornerShape(22.dp)).padding(vertical=25.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text("کلمهٔ مخصوص اجرا",color=Color(0xFF9EAEFF));Text(if(showWord)word else "برای نمایش کلمه بزن",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60),color=if(seconds<=10)Color(0xFFFF817D) else Color(0xFF8DE0C1),style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.Black);Text(if(seconds==0)"زمان تمام شد!" else "زمان باقی‌مانده",color=Color(0xFFADB8D0),style=MaterialTheme.typography.labelSmall)}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={if(!showWord){word=decks[category].orEmpty().random();showWord=true};running=!running},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7188FF)),shape=RoundedCornerShape(14.dp)){Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(if(running)"مکث" else "شروع زمان‌سنج")};OutlinedButton(onClick={next()},modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.SkipNext,null);Text("کلمه بعدی")}}
    Button(onClick={if(team==1)score1++ else score2++;next()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF8DE0C1),contentColor=Color(0xFF102019)),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.CheckCircle,null);Text("حدس درست بود! +۱")}
   }}}
   item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182541)),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("جدول امتیاز",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){listOf(Triple(team1,score1,1),Triple(team2,score2,2)).forEach{entry->Card(Modifier.weight(1f).clickable{team=entry.third},colors=CardDefaults.cardColors(containerColor=if(team==entry.third)Color(0xFF344A82) else Color(0xFF222E49)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){Text(if(entry.third==1)"تیم اول" else "تیم دوم",color=Color(0xFF9EAEFF),style=MaterialTheme.typography.labelSmall);Text(entry.first,color=Color.White,fontWeight=FontWeight.Bold);Text(entry.second.toString(),color=Color(0xFF8DE0C1),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("انتخاب نوبت",color=Color(0xFFADB8D0),style=MaterialTheme.typography.labelSmall)}}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={team=if(team==1)2 else 1;next()},modifier=Modifier.weight(1f)){Text("تعویض نوبت تیم")};TextButton(onClick={score1=0;score2=0;round=1;seconds=60;running=false;showWord=false;word="برای شروع، کلمه را دریافت کن"}){Text("شروع مجدد",color=Color(0xFFFFA7A0))}}}}}
   item { QuickMathGame() }
   item { ColorMemoryGame() }
  }
 }
}

@Composable private fun QuickMathGame(){
 var a by remember{mutableIntStateOf((3..12).random())}
 var b by remember{mutableIntStateOf((3..12).random())}
 var answer by remember{mutableStateOf("")}
 var points by remember{mutableIntStateOf(0)}
 var message by remember{mutableStateOf("جواب درست بده و رکوردت را بالا ببر!")}
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182541)),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("چالش محاسبه سریع",color=Color(0xFF9EAEFF),fontWeight=FontWeight.Bold)
   Text(a.toString()+" × "+b.toString()+" = ؟",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Black)
   Text("امتیاز: "+points,color=Color(0xFF8DE0C1),fontWeight=FontWeight.Bold)
   OutlinedTextField(answer,{answer=it.filter(Char::isDigit)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("پاسخ")})
   Button(onClick={if(answer.toIntOrNull()==a*b){points++;message="درست بود! عالیه."}else message="جواب درست "+(a*b)+" بود.";a=(3..12).random();b=(3..12).random();answer=""},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7188FF))){Text("بررسی و سؤال بعدی")}
   Text(message,color=Color(0xFFADB8D0),style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable private fun ColorMemoryGame(){
 val colors=remember{listOf(Color(0xFFE66B62),Color(0xFF6C9FE6),Color(0xFF65B98A),Color(0xFFE3BD61))}
 val names=remember{listOf("قرمز","آبی","سبز","زرد")}
 var sequence by remember{mutableStateOf(listOf<Int>())}
 var chosen by remember{mutableStateOf(listOf<Int>())}
 var level by remember{mutableIntStateOf(1)}
 var message by remember{mutableStateOf("الگو را ببین و همان ترتیب را تکرار کن.")}
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182541)),shape=RoundedCornerShape(22.dp)){
  Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("حافظه رنگ‌ها",color=Color(0xFF9EAEFF),fontWeight=FontWeight.Bold)
   Text("مرحله "+level,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text(message,color=Color(0xFFADB8D0),style=MaterialTheme.typography.bodySmall)
   if(sequence.isNotEmpty()) Text("الگو: "+sequence.joinToString(" ← "){names[it]},color=Color.White,fontWeight=FontWeight.SemiBold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){colors.forEachIndexed{index,c->Box(Modifier.weight(1f).height(58.dp).background(c,RoundedCornerShape(14.dp)).clickable{if(sequence.isNotEmpty()){val next=chosen+index;chosen=next;if(next.size==sequence.size){if(next==sequence){level++;sequence=sequence+(0..3).random();message="درست بود! مرحله بعدی آماده است."}else{level=1;sequence=emptyList();message="ترتیب اشتباه بود؛ دوباره شروع کن."};chosen=emptyList()}}},contentAlignment=Alignment.Center){Text(names[index],color=Color.Black,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)}}}
   Button(onClick={sequence=List((level+1).coerceAtMost(8)){(0..3).random()};chosen=emptyList();message="حالا رنگ‌ها را به همان ترتیب لمس کن."},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7188FF))){Text("ساخت الگوی مرحله بعد")}
  }
 }
}

private data class Film(val title:String,val year:Int,val genre:String,val duration:Int,val summary:String,val reason:String)
private val films=listOf(
 Film("Perfect Days",2023,"درام",124,"مردی در توکیو زیبایی‌های کوچک زندگی روزمره را کشف می‌کند.","مینیمال، آرام و انسانی."),
 Film("The Grand Budapest Hotel",2014,"کمدی، ماجراجویی",99,"ماجرایی پرجزئیات در هتلی باشکوه با شخصیت‌هایی عجیب.","برای لذت بردن از طراحی صحنه و رنگ."),
 Film("Arrival",2016,"علمی‌تخیلی، درام",116,"زبان‌شناسی برای ارتباط با موجوداتی فرازمینی فراخوانده می‌شود.","هوشمند، احساسی و فکری."),
 Film("In the Mood for Love",2000,"عاشقانه، درام",98,"دو همسایه در هنگ‌کنگ با تنهایی و احساسات ناگفته روبه‌رو می‌شوند.","تصویرسازی شاعرانه و موسیقی ماندگار."),
 Film("Whiplash",2014,"درام، موسیقی",107,"نوازنده‌ای جوان زیر فشار استادش برای کمال تلاش می‌کند.","پرکشش و پرانرژی."),
 Film("Spirited Away",2001,"انیمیشن، فانتزی",125,"دختری وارد جهانی جادویی می‌شود و باید شجاعت پیدا کند.","خیال‌انگیز با طراحی هنری فوق‌العاده."),
 Film("The Secret Life of Walter Mitty",2013,"ماجراجویی",114,"مردی خیال‌پرداز از روزمرگی بیرون می‌آید و سفر می‌کند.","برای حال‌وهوای سفر و شروع دوباره."),
 Film("Parasite",2019,"هیجان‌انگیز، درام",132,"برخورد دو خانواده از طبقات اجتماعی متفاوت به اتفاقاتی غیرمنتظره می‌رسد.","روایت چندلایه و غافلگیری."),
 Film("Her",2013,"عاشقانه، علمی‌تخیلی",126,"مردی تنها با یک سیستم‌عامل هوشمند رابطه‌ای عاطفی برقرار می‌کند.","برای فکر کردن به تنهایی و فناوری."),
 Film("Cinema Paradiso",1988,"درام",155,"خاطرات کودکی و دوستی پسری با آپاراتچی سینما در ایتالیا.","نوستالژیک و گرم؛ نامه‌ای عاشقانه به سینما.")
)
private data class Track(val title:String,val artist:String,val album:String,val preview:String)
private class TrackSearch{
 private val http=OkHttpClient()
 suspend fun search(q:String):List<Track> = withContext(Dispatchers.IO){
  val url="https://itunes.apple.com/search?term="+URLEncoder.encode(q,"UTF-8")+"&entity=song&limit=25"
  http.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful)throw IllegalStateException("جست‌وجوی موسیقی ناموفق بود");val a=JSONObject(r.body?.string().orEmpty()).optJSONArray("results")?:org.json.JSONArray();(0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val p=o.optString("previewUrl");if(p.isBlank()||p=="null")null else Track(o.optString("trackName"),o.optString("artistName"),o.optString("collectionName"),p)}}
 }
}
@Composable private fun FilmPoster(title:String){
 var bitmap by remember(title){mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)}
 LaunchedEffect(title){try{
  val url=withContext(Dispatchers.IO){val q=URLEncoder.encode(title,"UTF-8");val req=Request.Builder().url("https://itunes.apple.com/search?term="+q+"&entity=movie&limit=1").build();OkHttpClient().newCall(req).execute().use{res->JSONObject(res.body?.string().orEmpty()).optJSONArray("results")?.optJSONObject(0)?.optString("artworkUrl100")?.replace("100x100","600x900").orEmpty()}}
  if(url.isNotBlank()){val bytes=withContext(Dispatchers.IO){OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use{it.body?.bytes()}};if(bytes!=null)bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()}
 }catch(_:Exception){}}
 Box(Modifier.width(112.dp).height(164.dp).background(Color(0xFF30364A),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!,contentDescription=title,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Movie,null,tint=Color(0xFFD7B77D),modifier=Modifier.size(32.dp));Text("POSTER",color=Color(0xFFA8ADB8),style=MaterialTheme.typography.labelSmall)}}
}
@Composable fun DiscoverDashboard(onClose:()->Unit,onAskAi:(String)->Unit,initialTab:String="فیلم امروز",standalone:Boolean=false){
 val ctx=LocalContext.current;var tab by remember{mutableStateOf(initialTab)};var query by remember{mutableStateOf("")};var mood by remember{mutableStateOf("همه")};var tracks by remember{mutableStateOf<List<Track>>(emptyList())};var loading by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var playing by remember{mutableStateOf<String?>(null)};var player by remember{mutableStateOf<MediaPlayer?>(null)}
 val scope=rememberCoroutineScope();val search=remember{TrackSearch()};val today=films[((System.currentTimeMillis()/86400000L)%films.size).toInt()]
 DisposableEffect(Unit){onDispose{player?.runCatching{stop()};player?.runCatching{release()}}}
 Column(Modifier.fillMaxSize().background(FB).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(standalone)"موسیقی" else "سینما",color=FG,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(standalone)"جست‌وجو کن، گوش بده و هنرمندهای تازه پیدا کن" else "پوسترها، فیلم‌ها و پیشنهاد تماشا",color=FM)};IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=FG)}}
  if(!standalone) Row{listOf("فیلم امروز","جست‌وجوی فیلم").forEach{FilterChip(selected=tab==it,onClick={tab=it},label={Text(it)})}}
  if(tab=="فیلم امروز"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
    item{FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(today.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("انتخاب امروز",color=FG,fontWeight=FontWeight.Bold);Text(today.title,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(today.year.toString()+" • "+today.genre+" • "+today.duration+" دقیقه",color=FG,style=MaterialTheme.typography.labelSmall);Text(today.summary,color=Color.White,style=MaterialTheme.typography.bodySmall);Text(today.reason,color=FM,style=MaterialTheme.typography.bodySmall);Button(onClick={onAskAi("درباره فیلم "+today.title+" معرفی کامل بدون اسپویل و تحلیل سبک کارگردانی بنویس.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("تحلیل بیشتر")}}}}}
    item{Text("بر اساس حال‌وهوا",color=FG,fontWeight=FontWeight.Bold);Row{listOf("همه","آرام","پرکشش","علمی‌تخیلی","درام").forEach{FilterChip(selected=mood==it,onClick={mood=it},label={Text(it,style=MaterialTheme.typography.labelSmall)})}}}
    items(films.filter{mood=="همه"||it.genre.contains(mood)||(mood=="آرام"&&it.duration>=90)||(mood=="پرکشش"&&it.genre.contains("ماجراجویی"))}){f->FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(f.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(f.title,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(f.year.toString()+" • "+f.genre+" • "+f.duration+" دقیقه",color=FG,style=MaterialTheme.typography.labelSmall);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall);TextButton(onClick={onAskAi("معرفی و نقد بدون اسپویل فیلم "+f.title+" را بنویس.")}){Text("نقد و معرفی بیشتر",color=FG)}}}}}
   }
  }else if(tab=="جست‌وجوی فیلم"){
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
    item{FInput("نام فیلم یا ژانر",query,{query=it},Modifier.fillMaxWidth());Button(onClick={onAskAi("چند فیلم خوب دربارهٔ "+query+" معرفی کن و خلاصه و دلیل انتخابشان را بگو.")},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=FG,contentColor=FB)){Text("پیشنهاد با هوش مصنوعی")}}
    items(films.filter{query.isBlank()||it.title.contains(query,true)||it.genre.contains(query,true)}){f->FCard{Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){FilmPoster(f.title);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(f.title,color=Color.White,fontWeight=FontWeight.Bold);Text(f.year.toString()+" • "+f.genre,color=FG);Text(f.summary,color=FM,style=MaterialTheme.typography.bodySmall)}}}}
   }
  }else{
   Text("جست‌وجوی آهنگ و هنرمند",color=FG,fontWeight=FontWeight.Bold)
   Text("پخش داخل برنامه از پیش‌نمایش رسمی حدود ۳۰ ثانیه‌ای iTunes استفاده می‌کند.",color=FM,style=MaterialTheme.typography.bodySmall)
   Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("نام خواننده، آهنگ یا سبک")});IconButton(onClick={if(query.isNotBlank()){loading=true;error="";scope.launch{try{tracks=search.search(query)}catch(e:Exception){error=e.message?:"خطا"}finally{loading=false}}}}){Icon(Icons.Default.Search,null,tint=FG)}}
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=FG)
   if(error.isNotBlank())Text(error,color=Color(0xFFFF9A91))
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(vertical=8.dp)){
    items(tracks){t->Card(colors=CardDefaults.cardColors(containerColor=FC)){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t.title,color=Color.White,fontWeight=FontWeight.SemiBold);Text(t.artist+" • "+t.album,color=FM,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={if(playing==t.preview){player?.pause();playing=null}else{player?.runCatching{stop()};player?.runCatching{release()};player=MediaPlayer().apply{setDataSource(t.preview);setOnPreparedListener{it.start();playing=t.preview};setOnCompletionListener{playing=null;it.release()};prepareAsync()}}}){Icon(if(playing==t.preview)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=FG)}}}}
   }
   if(query.isNotBlank())TextButton(onClick={onAskAi("سبک موسیقی "+query+" را تحلیل کن، ویژگی‌های صوتی و حال‌وهوایش را توضیح بده و هنرمندان مشابه معرفی کن.")}){Text("تحلیل سبک موسیقی با هوش مصنوعی",color=FG)}
  }
 }
}

@Composable fun MusicDashboard(onClose:()->Unit,onAskAi:(String)->Unit){ DiscoverDashboard(onClose,onAskAi,"موسیقی",true) }
