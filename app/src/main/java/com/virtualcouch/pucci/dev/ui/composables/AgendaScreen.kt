package com.virtualcouch.pucci.dev.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.*
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity
import com.virtualcouch.pucci.dev.viewmodel.TikTokViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

private val VirtualCouchBlue = Color(0xFF1D4EEE)

@Composable
fun AgendaScreen(
    modifier: Modifier = Modifier,
    viewModel: TikTokViewModel = hiltViewModel()
) {
    val events by viewModel.calendarEvents.collectAsState(initial = emptyList())

    // Lazy Load: Sincroniza apenas quando abrir a tela
    LaunchedEffect(Unit) {
        viewModel.syncCalendar()
    }
    
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(100) }
    val endMonth = remember { currentMonth.plusMonths(100) }
    val daysOfWeek = remember { daysOfWeek() }
    
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var isMonthView by remember { mutableStateOf(true) }
    
    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = daysOfWeek.first()
    )
    val coroutineScope = rememberCoroutineScope()

    // Map events by date
    val eventsByDate = remember(events) {
        events.groupBy { 
            try {
                ZonedDateTime.parse(it.startTime).toLocalDate()
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header (Fixe)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Minha Agenda",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Virtual Couch",
                    color = VirtualCouchBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.syncCalendar() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sincronizar", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.DarkGray)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isMonthView) VirtualCouchBlue else Color.Transparent)
                            .clickable { isMonthView = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Mês", color = Color.White, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!isMonthView) VirtualCouchBlue else Color.Transparent)
                            .clickable { isMonthView = false }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Dia", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        if (isMonthView) {
            // Container para a visão mensal com suporte a swipe em toda a área
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.firstVisibleMonth) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 50) { // Swipe Right -> Mês Anterior
                                coroutineScope.launch {
                                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.previousMonth)
                                }
                            } else if (dragAmount < -50) { // Swipe Left -> Próximo Mês
                                coroutineScope.launch {
                                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.nextMonth)
                                }
                            }
                        }
                    }
            ) {
                MonthHeader(
                    visibleMonth = state.firstVisibleMonth.yearMonth,
                    goToPrevious = {
                        coroutineScope.launch {
                            state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.previousMonth)
                        }
                    },
                    goToNext = {
                        coroutineScope.launch {
                            state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.nextMonth)
                        }
                    }
                )
                
                HorizontalCalendar(
                    state = state,
                    userScrollEnabled = true,
                    dayContent = { day ->
                        val hasEvents = eventsByDate.containsKey(day.date)
                        Day(
                            day = day,
                            isSelected = selectedDate == day.date,
                            hasEvents = hasEvents,
                            onClick = {
                                selectedDate = it.date
                                isMonthView = false 
                            }
                        )
                    },
                    monthHeader = {
                        DaysOfWeekTitle(daysOfWeek = daysOfWeek)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                // Espaço extra preenchido para garantir que o swipe funcione até o fim da tela
                Box(modifier = Modifier.fillMaxSize())
            }
        } else {
            DayView(
                selectedDate = selectedDate,
                events = eventsByDate[selectedDate] ?: emptyList(),
                onDateSelected = { selectedDate = it }
            )
        }
    }
}

@Composable
fun Day(
    day: CalendarDay, 
    isSelected: Boolean, 
    hasEvents: Boolean,
    onClick: (CalendarDay) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (isSelected) VirtualCouchBlue else Color.Transparent)
            .clickable(
                enabled = day.position == DayPosition.MonthDate,
                onClick = { onClick(day) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = when {
                    isSelected -> Color.White
                    day.position == DayPosition.MonthDate -> Color.White
                    else -> Color.DarkGray
                },
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (hasEvents && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(VirtualCouchBlue)
                )
            }
        }
    }
}

@Composable
fun DayView(
    selectedDate: LocalDate,
    events: List<EventEntity>,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedDate) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount > 50) { // Swipe Right -> Dia Anterior
                        onDateSelected(selectedDate.minusDays(1))
                    } else if (dragAmount < -50) { // Swipe Left -> Próximo Dia
                        onDateSelected(selectedDate.plusDays(1))
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { onDateSelected(selectedDate.minusDays(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White)
            }
            Text(
                text = "${selectedDate.dayOfMonth} de ${selectedDate.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(onClick = { onDateSelected(selectedDate.plusDays(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
            }
        }

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma sessão para este dia", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events.sortedBy { it.startTime }) { event ->
                    val time = try {
                        ZonedDateTime.parse(event.startTime)
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) {
                        "--:--"
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VirtualCouchBlue.copy(alpha = 0.15f))
                            .border(1.dp, VirtualCouchBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = time,
                            color = VirtualCouchBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp)
                        )
                        Divider(
                            modifier = Modifier
                                .height(40.dp)
                                .width(1.dp)
                                .padding(horizontal = 8.dp),
                            color = Color.DarkGray
                        )
                        Column {
                            Text(
                                text = event.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (event.patientName.isNotBlank()) {
                                Text(
                                    text = "Paciente: ${event.patientName}",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthHeader(
    visibleMonth: YearMonth,
    goToPrevious: () -> Unit,
    goToNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val monthName = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
        Text(
            text = "$monthName ${visibleMonth.year}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Row {
            IconButton(onClick = goToPrevious) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior", tint = Color.White)
            }
            IconButton(onClick = goToNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Próximo", tint = Color.White)
            }
        }
    }
}

@Composable
fun DaysOfWeekTitle(daysOfWeek: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR")).uppercase(),
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
