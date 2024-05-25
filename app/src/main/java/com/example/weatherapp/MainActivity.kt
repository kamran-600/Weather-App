package com.example.weatherapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.example.weatherapp.adapter.RvAdapter
import com.example.weatherapp.data.forecastModels.ForecastData
import com.example.weatherapp.data.util.RetrofitInstance
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.databinding.BottomSheetLayoutBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


@SuppressLint("SetTextI18n")
@OptIn(DelicateCoroutinesApi::class)
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sheetLayoutBinding: BottomSheetLayoutBinding
    private lateinit var dialog : BottomSheetDialog
    private lateinit var pollutionFragment: PollutionFragment
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var city = "Lucknow"
    private lateinit var task : Task<Location>

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val animationDrawable = binding.root.background as AnimationDrawable
        animationDrawable.setEnterFadeDuration(2500)
        animationDrawable.setExitFadeDuration(5000)
        animationDrawable.start()


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        fetchLocationAndGetWeatherInfo()

        binding.location.setOnClickListener{
            fetchLocationAndGetWeatherInfo()
        }



        pollutionFragment = PollutionFragment()

        binding.root.setOnTouchListener(object : View.OnTouchListener{
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                return if(currentFocus != null){
                    imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
                    binding.searchView.clearFocus()
                    true
                } else false
            }

        })




        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener,
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if(query != null){
                    city = query.trim()
                    getCurrentWeather(city)
                }
                binding.searchView.clearFocus()

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }

        })



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }else{
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }


        binding.tvForecast.setOnClickListener{
            openDialog()
        }

    }



    private fun fetchLocationAndGetWeatherInfo() {

        val permissionToRequest = mutableListOf<String>()

        val locationPermissions = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

        for(permission in locationPermissions){
            if(ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                permissionToRequest.add(permission)
            }
        }
        if(permissionToRequest.isNotEmpty()){
            locationPermissionLauncher.launch(permissionToRequest.toTypedArray())
        }
        else{

            task = fusedLocationProviderClient.lastLocation

            task.addOnSuccessListener {

                val geocoder = Geocoder(this, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(it.latitude, it.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                city = addresses[0].locality
                            }

                        })
                } else {
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                    city = addresses?.get(0)?.locality.toString()
                }

                getCurrentWeather(city)


            }
        }

    }


    @SuppressLint("MissingPermission")
    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {  map ->
                val allPermissionsGranted = map.values.all { it }
                if(allPermissionsGranted){

                    task = fusedLocationProviderClient.lastLocation

                    task.addOnSuccessListener {

                        val geocoder = Geocoder(this, Locale.getDefault())

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            geocoder.getFromLocation(it.latitude, it.longitude, 1,
                                object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        city = addresses[0].locality
                                    }

                                })
                        } else {
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            city = addresses?.get(0)?.locality.toString()
                        }

                        getCurrentWeather(city)


                    }
                }
    }

    private fun openDialog() {

        getForecast(binding.location.text.split(",")[0])

        sheetLayoutBinding = BottomSheetLayoutBinding.inflate(layoutInflater)

        dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        dialog.setContentView(sheetLayoutBinding.root)

        sheetLayoutBinding.rvForecast.apply {
            setHasFixedSize(true)

        }

        dialog.show()
    }


    private fun getForecast(city : String) {

        GlobalScope.launch(Dispatchers.IO) {
            val response = try {
                RetrofitInstance.api.getForecast(city, "metric", getString(R.string.api_key))
            }catch (e : IOException){
            withContext(Dispatchers.Main){
                Toast.makeText(applicationContext, "IO error ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return@launch
            }catch (e : HttpException){
                withContext(Dispatchers.Main){
                    Toast.makeText(applicationContext, "HTTP error ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            if(response.isSuccessful && response.body()!= null){
                withContext(Dispatchers.Main){

                    val data = response.body()!!

                    val forecastArray = data.list as ArrayList<ForecastData>

                    val adapter = RvAdapter(forecastArray)
                    sheetLayoutBinding.rvForecast.adapter = adapter
                    sheetLayoutBinding.tvSheet.text =  "Five days Forecast in ${data.city.name}"




                }
            }
        }
    }




    private fun getCurrentWeather(city : String) {
        GlobalScope.launch(Dispatchers.IO) {
            val response = try {
                RetrofitInstance.api.getCurrentWeather(city, "metric", getString(R.string.api_key) )
            }catch (e : IOException){
                withContext(Dispatchers.Main){
                    Toast.makeText(applicationContext, "IO error ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }catch (e : HttpException){
                withContext(Dispatchers.Main){
                    Toast.makeText(applicationContext, "HTTP error ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            if(response.isSuccessful && response.body()!= null){
                withContext(Dispatchers.Main){

                    val  data = response.body()!!
                    val iconId = data.weather[0].icon
                    val imageUrl = "https://openweathermap.org/img/wn/${iconId}@4x.png"

                    Glide.with(this@MainActivity).load(imageUrl).into(binding.imageWeather)

                    binding.tvSunrise.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(data.sys.sunrise.toLong() * 1000))

                    binding.tvSunset.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(data.sys.sunset.toLong() * 1000))

                    binding.apply {
                        weatherDesc.text = data.weather[0].description
                        tvWindSpeed.text =  String.format("%.2f", data.wind.speed * 3.6).plus("KM/H")
                        location.text = "${data.name}, ${data.sys.country}"
                        temperature.text = "${data.main.temp.roundToInt()}\u2103"
                        minTemp.text = "Min Temp: ${data.main.temp_min.roundToInt()}\u2103"
                        maxTemp.text = "Max Temp: ${data.main.temp_max.roundToInt()}\u2103"
                        tvHumidity.text = "${data.main.humidity}%"
                        tvPressure.text = "${data.main.pressure}hPa"
                        updatedAt.text = "Last Update: ${
                            SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(data.dt.toLong() * 1000))
                        }"

                        getPollutionInfo(data.coord.lat, data.coord.lon)


                    }


                }
            }
        }
    }

    private fun getPollutionInfo(lat: Double, lon: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            val response = try {
                RetrofitInstance.api.getPollutionInfo(lat, lon, "metric", getString(R.string.api_key) )
            }catch (e : IOException){
                withContext(Dispatchers.Main){
                    Toast.makeText(applicationContext, "IO error ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }catch (e : HttpException){
                withContext(Dispatchers.Main){
                    Toast.makeText(applicationContext, "HTTP error ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            if(response.isSuccessful && response.body()!= null){
                withContext(Dispatchers.Main){

                    val data = response.body()!!

                    val aqi = data.list[0].main.aqi

                    binding.tvAirQuality.text = when(aqi){
                        1 -> "good"
                        2 -> "fair"
                        3 -> "moderate"
                        4 -> "poor"
                        5 -> "very poor"
                        else -> "no data"
                    }

                    binding.pollutionGrid.setOnClickListener {
                        val bundle = Bundle()
                        bundle.putDouble("co", data.list[0].components.co)
                        bundle.putDouble("nh3", data.list[0].components.nh3)
                        bundle.putDouble("no", data.list[0].components.no)
                        bundle.putDouble("no2", data.list[0].components.no2)
                        bundle.putDouble("o3", data.list[0].components.o3)
                        bundle.putDouble("pm10", data.list[0].components.pm10)
                        bundle.putDouble("pm2_5", data.list[0].components.pm2_5)
                        bundle.putDouble("so2", data.list[0].components.so2)


                        pollutionFragment.arguments = bundle

                        supportFragmentManager.beginTransaction().apply {
                            replace(R.id.container,pollutionFragment)
                                .addToBackStack(null)
                                .commit()
                        }

                    }

                }
            }
        }
    }
}