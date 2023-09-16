package com.uynaity.ezcc2

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner


@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {
    private lateinit var courseCodeEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var clearButton: ImageButton
    private lateinit var switch1: Switch
    private lateinit var sem1Stat: TextView
    private lateinit var sem1Data: TextView
    private lateinit var sem2Stat: TextView
    private lateinit var sem2Data: TextView
    private var backPressedTime: Long = 0
    private val backPressedInterval = 2000
    private var isSorted = false

    private var caches = mutableListOf(
        "", ""
    )

    private var cachesSorted = mutableListOf(
        "", ""
    )

    private var stats = mutableListOf(
        "", ""
    )

    private var semList = mutableListOf(
        mutableListOf<Map<String, Any>>(), mutableListOf()
    )

    private var sortedSemList = mutableListOf(
        mutableListOf<Map<String, Any>>(), mutableListOf()
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        courseCodeEditText = findViewById(R.id.courseCode)
        searchButton = findViewById(R.id.search)
        clearButton = findViewById(R.id.clearButton)
        switch1 = findViewById(R.id.switch1)
        sem1Stat = findViewById(R.id.sem1Stat)
        sem1Data = findViewById(R.id.Sem1Data)
        sem2Stat = findViewById(R.id.Sem2Stat)
        sem2Data = findViewById(R.id.Sem2Data)

        val spinner = findViewById<Spinner>(R.id.spinner)
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.spinner_options, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        searchButton.setOnClickListener {
            Thread {
                try {
                    semList = getTableData(courseCodeEditText.text.toString())
                    sortedSemList = semList.map { sem ->
                        sem.sortedByDescending { it["空余数量"] as Int }.toMutableList()
                    }.toMutableList()
                    if (isSorted) {
                        judgement()
                    } else {
                        judgement()
                    }
                    printSem()
                } catch (e: Exception) {
                    sem1Stat.text = e.message
                }
            }.start()

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchButton.windowToken, 0)
        }

        clearButton.setOnClickListener {
            courseCodeEditText.text.clear()
            courseCodeEditText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(courseCodeEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        switch1.setOnCheckedChangeListener { _, isChecked ->
            isSorted = isChecked
            if (semList[0].isNotEmpty() && semList[1].isNotEmpty()) {
                printSem()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (System.currentTimeMillis() - backPressedTime < backPressedInterval) {
            super.onBackPressed()
            finishAffinity()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(this, "再次点击返回键退出", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTableData(c: String?): MutableList<MutableList<Map<String, Any>>> {
        val spinner = findViewById<Spinner>(R.id.spinner)
        val selectedValue = spinner.selectedItem.toString()
        val mergeC = "$selectedValue$c"
        val url = URL("https://sweb.hku.hk/ccacad/ccc_appl/enrol_stat.html")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val responseCode = conn.responseCode
        if (responseCode != 200) {
            runOnUiThread {
                sem1Stat.text = responseCode.toString()
            }
            return mutableListOf()
        }

        val doc = Jsoup.parse(url, 3000)
        val table = doc.select("table").first()

        val semList = mutableListOf(
            mutableListOf<Map<String, Any>>(), mutableListOf()
        )

        var sem = 0

        for (row in table?.select("tr")!!) {
            val tds = row?.select("td")

            if (tds?.size!! < 6) {
                if (row.text() == "First Semester") {
                    sem = 0
                } else if (row.text() == "Second Semester") {
                    sem = 1
                }
                continue
            }

            if (!tds[0].text().contains(mergeC!!)) {
                continue
            }

            if (!tds[2].text().contains("X")) {
                val data1 = tds[0].text()
                val data2 = tds[4].text().toInt()
                val data3 = tds[5].text().toInt()
                var stat = ""

                if (data2 > data3) {
                    stat = "尚有余，可选\n"
                } else if (data3 - data2 <= 3) {
                    stat = "少量不足，可备选\n"
                } else if (data3 - data2 > 3) {
                    stat = "严重不足，建议更换\n"
                }
                val dataSet = mapOf(
                    "课程代码" to data1,
                    "空余数量" to data2,
                    "等待批准" to data3,
                    "选课建议" to stat
                )
                semList[sem].add(dataSet)
            }
        }
        return semList
    }


    @SuppressLint("SetTextI18n")
    private fun judgement() {
        for (index in semList.indices) {
            val subList = semList[index]
            if (subList.isEmpty()) {
                stats[index] = "未在Sem ${index + 1}中找到相关课程 :-("
            } else {
                stats[index] = "Sem ${index + 1}中找到以下相关课程:"
            }
            caches[index] = formatSem(subList)
        }

        for (index in sortedSemList.indices) {
            val subList = sortedSemList[index]
            cachesSorted[index] = formatSem(subList)
        }

    }

    private fun formatSem(sem: MutableList<Map<String, Any>>): String {
        var str = ""
        for (i in sem) {
            for (j in i) {
                str += "${j.key}: ${j.value}\n"
            }
        }
        str += "\n"
        return str
    }

    private fun printSem() {
        runOnUiThread {
            sem1Stat.text = stats[0]
            sem2Stat.text = stats[1]
            if (isSorted) {
                sem1Data.text = cachesSorted[0]
                sem2Data.text = cachesSorted[1]
            } else {
                sem1Data.text = caches[0]
                sem2Data.text = caches[1]
            }
        }
    }
}
