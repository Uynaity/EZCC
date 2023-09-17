package com.uynaity.ezcc2

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Switch
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {
    private lateinit var courseCodeEditText: EditText
    private lateinit var refreshButton: Button
    private lateinit var switch1: Switch
    private lateinit var switch2: Switch
    private lateinit var switch3: Switch
    private lateinit var sem1Stat: TextView
    private lateinit var sem1Data: TextView
    private lateinit var sem2Stat: TextView
    private lateinit var sem2Data: TextView
    private var backPressedTime: Long = 0
    private val backPressedInterval = 2000
    private var isSorted = false
    private var isExcept = false
    private var isAvailable = false

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

    object VibrationUtil {
        fun vibrate(context: Context, milliseconds: Long) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                val vibrationEffect =
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        courseCodeEditText = findViewById(R.id.courseCode)
        refreshButton = findViewById(R.id.refresh)
        switch1 = findViewById(R.id.switch1)
        switch2 = findViewById(R.id.switch2)
        switch3 = findViewById(R.id.switch3)
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

        Toast.makeText(this, "正在更新数据库", Toast.LENGTH_SHORT).show()
        updateTableData()
        Toast.makeText(this, "数据库更新成功", Toast.LENGTH_SHORT).show()


        courseCodeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                courseCodeEditText.requestFocus()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                courseCodeEditText.requestFocus()
            }

            override fun afterTextChanged(s: Editable?) {
                judgement()
                printSem()
            }
        })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long
            ) {
                while (semList[0].isEmpty() || semList[1].isEmpty()) {
                    Thread.sleep(100)
                }
                judgement()
                printSem()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        refreshButton.setOnClickListener {
            updateTableData()
            judgement()
            printSem()
            VibrationUtil.vibrate(this, 50)
            Toast.makeText(this, "刷新成功", Toast.LENGTH_SHORT).show()
        }

        switch1.setOnCheckedChangeListener { _, isChecked ->
            isSorted = isChecked
            VibrationUtil.vibrate(this, 50)
            if (semList[0].isNotEmpty() && semList[1].isNotEmpty()) {
                printSem()
            }
        }

        switch2.setOnCheckedChangeListener { _, isChecked ->
            isExcept = isChecked
            VibrationUtil.vibrate(this, 50)
            judgement()
            printSem()
        }

        switch3.setOnCheckedChangeListener { _, isChecked ->
            isAvailable = isChecked
            VibrationUtil.vibrate(this, 50)
            judgement()
            printSem()
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

    private fun updateTableData() {
        Thread {
            try {
                semList = getTableData()
                sortedSemList = semList.map { sem ->
                    sem.sortedByDescending { it["空余数量"] as Int }.toMutableList()
                }.toMutableList()
            } catch (e: Exception) {
                sem1Stat.text = e.message
            }
        }.start()
    }

    private fun getTableData(): MutableList<MutableList<Map<String, Any>>> {
        val url = URL("https://sweb.hku.hk/ccacad/ccc_appl/enrol_stat.html")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val responseCode = conn.responseCode
        if (responseCode != 200) {
            runOnUiThread {
                Toast.makeText(this, responseCode.toString(), Toast.LENGTH_SHORT).show()
            }
            return mutableListOf()
        }

        val doc = Jsoup.parse(url, 3000)
        val table = doc.select("table").first()
        val semList = mutableListOf(
            mutableListOf<Map<String, Any>>(), mutableListOf()
        )
        var sem = 0
        val tds = table?.select("tr")!!

        tds.removeAt(0)

        for (row in tds) {
            val td = row?.select("td")

            if (td?.size!! < 6) {
                if (row.text() == "First Semester") {
                    sem = 0
                } else if (row.text() == "Second Semester") {
                    sem = 1
                }
                continue
            }

            val data1 = td[0].text()
            val data2 = td[4].text().toInt()
            val data3 = td[5].text().toInt()
            val data4 = td[2].text()
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
                "班级代码" to data4,
                "空余数量" to data2,
                "等待批准" to data3,
                "选课建议" to stat
            )
            semList[sem].add(dataSet)
        }
        return semList
    }

    @SuppressLint("SetTextI18n")
    private fun judgement() {
        for (index in semList.indices) {
            val subList = semList[index]
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
            val spinner = findViewById<Spinner>(R.id.spinner)
            val selectedValue = spinner.selectedItem.toString()
            val c = courseCodeEditText.text.toString()
            val mergeC = "$selectedValue$c"
            if (!i["课程代码"].toString().contains(mergeC)) {
                continue
            }
            if (isExcept) {
                if (i["班级代码"].toString().contains("X")) {
                    continue
                }
            }
            if (isAvailable) {
                if (i["空余数量"] == 0) {
                    continue
                }
            }

            for (j in i) {
                str += "${j.key}: ${j.value}\n"
            }
        }
        str += "\n"
        return str
    }

    private fun printSem() {
        runOnUiThread {
            for (index in stats.indices) {
                if (caches[index] == "\n") {
                    stats[index] = "未在Sem ${index + 1}中找到相关课程 :-("
                } else {
                    stats[index] = "Sem ${index + 1}中找到以下相关课程:"
                }
            }
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
