package com.example.weatherapp.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.weatherapp.data.forecastModels.ForecastData
import com.example.weatherapp.databinding.SingleItemBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class RvAdapter(private val forecastArray: ArrayList<ForecastData>) : RecyclerView.Adapter<RvAdapter.ViewHolder>() {

    class ViewHolder(val binding: SingleItemBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(SingleItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int {
        return forecastArray.size
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = forecastArray[position]
        holder.binding.apply {

            val imageIcon = currentItem.weather[0].icon
            val imageUrl = "https://openweathermap.org/img/wn/$imageIcon@4x.png"

            Glide.with(imgItem.context).load(imageUrl).into(imgItem)

            tvItemTemp.text = "${currentItem.main.temp.roundToInt()} \u2103"
            tvItemStatus.text = "${currentItem.weather[0].description}"
            tvItemStatus.isSelected = true
            tvItemTime.text = displayTime(currentItem.dt_txt)

        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun displayTime(dateTimeText: String): CharSequence? {
            val input = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val output = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            val dateTime = LocalDateTime.parse(dateTimeText, input)
            return output.format(dateTime)

    }
}