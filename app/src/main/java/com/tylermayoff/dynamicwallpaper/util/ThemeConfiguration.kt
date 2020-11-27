package com.tylermayoff.dynamicwallpaper.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tylermayoff.dynamicwallpaper.util.CustomUtilities.*
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileFilter
import java.nio.file.Files
import java.time.LocalTime
import java.util.*

class ThemeConfiguration() {

    private val imageFilter : FileFilter = FileFilter { pathname ->
        val mimeType = Files.probeContentType(pathname.toPath())
        mimeType.startsWith("image/")
    }

    private val jsonFilter : FileFilter = FileFilter { pathname ->
        val mimeType = Files.probeContentType(pathname.toPath())
        mimeType == "application/json"
    }
    var images = mutableListOf<Bitmap>()
    var wallpaperChangeTimes = mutableListOf<LocalTime>()
    var nightTimes = mutableListOf<LocalTime>()
    var sunriseTimes = mutableListOf<LocalTime>()
    var dayTimes = mutableListOf<LocalTime>()
    var sunsetTimes = mutableListOf<LocalTime>()

    var useSunsetSunrise : Boolean = false
    lateinit var themeConfig : ThemeConfig

    constructor(context: Context, themeName : String) : this() {
        val themeDir = File(context.filesDir.absolutePath + "/themes/" + themeName)

        val imageFiles : Array<File> = themeDir.listFiles(imageFilter) ?: return

        Arrays.sort(imageFiles) {f1, f2 -> compareNatural(f1.name, f2.name) }

        for (image : File in imageFiles) {
            val b : Bitmap = BitmapFactory.decodeFile(image.absolutePath)
            images.add(b)
        }

        val appSettings = AppSettings.getInstance(context)
        useSunsetSunrise = appSettings.useSunsetSunrise
        if (appSettings.sunsetTime == null || appSettings.sunriseTime == null)
            useSunsetSunrise = false

        // Get theme.json configuration
        val configFile : Array<File>? = themeDir.listFiles(jsonFilter)
        if (configFile != null) {
            val gson : Gson = GsonBuilder().create()
            val jsonString : String = FileUtils.readFileToString(configFile[0], "UTF-8")
            themeConfig = gson.fromJson(jsonString, ThemeConfig::class.java)

            if (useSunsetSunrise) {
                // Day times
                val sunsetSunriseLength = 10

                val dayLength: Int = ((appSettings.sunsetTime!!.timeInMillis - appSettings.sunriseTime!!.timeInMillis).toInt() / 1000 / 60) - sunsetSunriseLength
                val nightLength: Int = (24 * 60) - dayLength - sunsetSunriseLength

                val dayIntervals = dayLength / themeConfig.dayImageList.size
                val nightIntervals = nightLength / themeConfig.nightImageList.size
                val sunriseIntervals = sunsetSunriseLength / themeConfig.sunriseImageList.size
                val sunsetIntervals = sunsetSunriseLength / themeConfig.sunsetImageList.size

                // Sunrise
                var startCal: Calendar = appSettings.sunriseTime!!.clone() as Calendar
                var startTime = LocalTime.of(startCal.get(Calendar.HOUR_OF_DAY), Calendar.MINUTE)
                for (i in themeConfig.sunriseImageList) {
                    sunriseTimes.add(startTime)
                    startTime = startTime.plusMinutes(sunriseIntervals.toLong())
                }

                // Times day
                for (i in themeConfig.dayImageList) {
                    dayTimes.add(startTime)
                    startTime = startTime.plusMinutes(dayIntervals.toLong())
                }

                // Sunset
                startCal = appSettings.sunsetTime!!.clone() as Calendar
                startTime = LocalTime.of(startCal.get(Calendar.HOUR_OF_DAY), Calendar.MINUTE)
                for (i in themeConfig.sunsetImageList) {
                    sunsetTimes.add(startTime)
                    startTime = startTime.plusMinutes(sunsetIntervals.toLong())
                }

                // Night
                for (i in themeConfig.nightImageList) {
                    nightTimes.add(startTime)
                    startTime = startTime.plusMinutes(nightIntervals.toLong())
                }
            }
        }

        if (!useSunsetSunrise) {
            val timeIncrements = 24 * 60 / images.size
            var startCal = LocalTime.of(0, 0, 0)

            for (i in 0..images.size) {
                wallpaperChangeTimes.add(startCal)
                startCal = startCal.plusMinutes(timeIncrements.toLong())
            }
        }
    }

    fun getCurrentBitmap (): Bitmap {
        return if (useSunsetSunrise)
            getBitmap(getCurrentTimeIndex())
        else {
            images[getCurrentTimeIndexWithoutSun()]
        }
    }

    fun getNextChangeTime(): LocalTime {
        return if (useSunsetSunrise)
            getTime(getCurrentTimeIndex() + 1)
        else
            getNextTimeChangeWithoutSun()
    }

    private fun getCurrentTimeIndex(): Int {
        val now = LocalTime.now()
        var timeIndex = 0

        var last = sunriseTimes[0]
        for (i in 1..sunriseTimes.size) {
            timeIndex++
            if (i == sunriseTimes.size) break
            if (now  >= last && now <= sunriseTimes[i]) {
                return timeIndex - 1
            }
            last = sunriseTimes[i]
        }

        last = dayTimes[0]
        for (i in 1..dayTimes.size) {
            timeIndex++
            if (i == dayTimes.size) break
            if (now  >= last && now <= dayTimes[i]) {
                return timeIndex - 1
            }
            last = dayTimes[i]
        }

        last = sunsetTimes[0]
        for (i in 1..sunsetTimes.size) {
            timeIndex++
            if (i == sunsetTimes.size) break
            if (now  >= last && now <= sunsetTimes[i]) {
                return timeIndex - 1
            }
            last = sunsetTimes[i]
        }

        last = nightTimes[0]
        for (i in 1..nightTimes.size) {
            timeIndex++
            if (i == nightTimes.size) break
            if (now  >= last && now <= nightTimes[i]) {
                return timeIndex - 1
            }
            last = nightTimes[i]
        }

        return 0
    }

    private fun getTime(i: Int): LocalTime {
        var index: Int = i
        if (index < sunriseTimes.size) return sunriseTimes[index]
        index -= sunriseTimes.size
        if (index < dayTimes.size) return dayTimes[index]
        index -= dayTimes.size
        if (index < sunsetTimes.size) return sunsetTimes[index]
        index -= sunsetTimes.size
        if (index < nightTimes.size) return nightTimes[index]

        return nightTimes[0]
    }

    private fun getBitmap (i: Int): Bitmap {
        var index: Int = i
        if (index < sunriseTimes.size) return images[themeConfig.sunriseImageList[index] - 1]
        index -= sunriseTimes.size
        if (index < dayTimes.size) return images[themeConfig.dayImageList[index] - 1]
        index -= dayTimes.size
        if (index < sunsetTimes.size) return images[themeConfig.sunsetImageList[index] - 1]
        index -= sunsetTimes.size
        if (index < nightTimes.size) return images[themeConfig.nightImageList[index] - 1]

        return images[themeConfig.nightImageList[0]]
    }

    private fun getCurrentTimeIndexWithoutSun () : Int {
        if (wallpaperChangeTimes.size == 0) return -1

        var lastCal = wallpaperChangeTimes[0]
        val now = LocalTime.now()

        for (i in 1 until wallpaperChangeTimes.size) {
            val currentIndexTime = wallpaperChangeTimes[i]
            val lastIndexTime = lastCal
            if (now >= lastIndexTime && now <= currentIndexTime) {
                return i - 1
            }
            lastCal = wallpaperChangeTimes[i]
        }

        return 0
    }

    private fun getNextTimeChangeWithoutSun() : LocalTime {
        val lastIndex = getCurrentTimeIndexWithoutSun()
        if (lastIndex == -1) return wallpaperChangeTimes[0]
        val index = (lastIndex + 1) % wallpaperChangeTimes.size
        return wallpaperChangeTimes[index]
    }

    inner class ThemeConfig {
        var imageFilename : String = ""
        var imageCredits : String = ""
        var sunriseImageList: Array<Int> = arrayOf()
        var dayImageList: Array<Int> = arrayOf()
        var sunsetImageList: Array<Int> = arrayOf()
        var nightImageList: Array<Int> = arrayOf()
    }
}