package com.puzzlebench.rappelmoi.form

import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.puzzlebench.rappelmoi.R
import com.puzzlebench.rappelmoi.components.AlarmScheduler
import com.puzzlebench.rappelmoi.components.EventWorker
import com.puzzlebench.rappelmoi.database.Event
import com.puzzlebench.rappelmoi.database.RappelMoiDatabase
import com.puzzlebench.rappelmoi.formatDate
import kotlinx.android.synthetic.main.activity_form_reminder.*
import kotlinx.android.synthetic.main.activity_form_reminder.tv_date
import java.text.SimpleDateFormat
import java.util.*


class FormReminderActivity : AppCompatActivity(), DatePickerDialog.OnDateSetListener,
    TimePickerDialog.OnTimeSetListener {

    companion object {
        private const val EXTRA_EVENT_ID = "EXTRA_ID_THEM"

        fun getIntent(context: Context, eventId: Long): Intent {
            return Intent(context, FormReminderActivity::class.java).putExtra(
                EXTRA_EVENT_ID,
                eventId
            )
        }
    }

    private lateinit var viewModel: FromReminderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_reminder)
        val dataSource = RappelMoiDatabase.getInstance(this.applicationContext).evenDao
        val viewModelFactory = FromReminderViewModelFactory(dataSource)
        viewModel = ViewModelProvider(this, viewModelFactory).get(FromReminderViewModel::class.java)
        val calendar: Calendar = Calendar.getInstance()
        calendar.run {
            showDate(this.timeInMillis)
            showTime(this.timeInMillis)
        }
        viewModel.viewStateLiveData.observe(::getLifecycle, ::handleViewState)

        createNotificationChannel(
            getString(R.string.event_notification_channel_id),
            getString(R.string.event_notification_channel_name)
        )
        tv_date.setOnClickListener {
            showDatePickerDialog()
        }
        tv_time.setOnClickListener {
            showTimePickerDialog()
        }
        btn_save.setOnClickListener {
            viewModel.saveEvent(
                et_name.text.toString()
                , et_description.text.toString(),
                tv_date.text.toString(),
                tv_time.text.toString(),
                options_sp.selectedItem.toString()
            )
        }
        initSelector()

    }

    private fun initSelector() {
        ArrayAdapter.createFromResource(
            this,
            R.array.options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            options_sp.adapter = adapter
        }
    }


    private fun createNotificationChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
                .apply {
                    setShowBadge(false)
                }

            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.enableVibration(true)
            notificationChannel.description =
                getString(R.string.event_notification_channel_description)

            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(notificationChannel)

        }
    }

    private fun handleViewState(fromState: FormState) = when (fromState) {
        is FormState.ShowEmptyNameError -> {
            tf_name.error = getString(R.string.error_message_empty_name)
        }
        is FormState.ShowInvalidDateError -> {
            Toast.makeText(
                this,
                getString(R.string.error_message_invalid_date),
                Toast.LENGTH_SHORT
            ).show()
        }
        is FormState.SaveSuccessFull -> {
            AlarmScheduler.scheduleAlarmsForReminder(this.applicationContext, fromState.event)
            tf_name.error = ""
        }
        is FormState.SaveSuccessFullUseWorker -> {
            useWork(fromState.event)
            tf_name.error = ""
        }
        is FormState.ShowMessage -> {
            Toast.makeText(
                this,
                fromState.message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun useWork(event: Event) {
        val workerConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true).build()

        val eventWorker = OneTimeWorkRequestBuilder<EventWorker>()
            .setConstraints(workerConstraints)
            .setInputData(EventWorker.setEventWorkerData(event.id))
            .build()

        val workManager = WorkManager.getInstance(this.applicationContext)
        workManager.enqueue(eventWorker)

    }

    private fun showDatePickerDialog() {
        val calendar: Calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            this,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate =
            calendar.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        datePickerDialog.show()
    }


    private fun showTimePickerDialog() {
        val calendar: Calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this, this, calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun showDate(date: Long) {
        tv_date.text = date.formatDate("MM dd yyyy")
    }

    private fun showTime(dateTime: Long) {
        tv_time.text = dateTime.formatDate("HH:mm")

    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, day: Int) {
        val date = Calendar.getInstance()
        date.set(Calendar.YEAR, year)
        date.set(Calendar.MONTH, month)
        date.set(Calendar.DAY_OF_MONTH, day)
        showDate(date.timeInMillis)
    }

    override fun onTimeSet(view: TimePicker?, hour: Int, minute: Int) {
        val datetime = Calendar.getInstance()
        datetime.set(Calendar.HOUR_OF_DAY, hour)
        datetime.set(Calendar.MINUTE, minute)
        showTime(datetime.timeInMillis)
    }
}