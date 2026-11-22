package com.tertiaryinfotech.ailmstms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tertiaryinfotech.ailmstms.LmsViewModel
import com.tertiaryinfotech.ailmstms.Load
import com.tertiaryinfotech.ailmstms.data.Payout
import com.tertiaryinfotech.ailmstms.data.money
import com.tertiaryinfotech.ailmstms.data.prettyDay

// The role tabs between Calendar and Account. Trainer: Payout, Hours. Learner: Certificates, Billing.

// ---------------------------------------------------------------- Payout (trainer)

/** Every completed payout for the classes the trainer taught, with the total on top. */
@Composable
fun PayoutScreen(vm: LmsViewModel) {
    LaunchedEffect(vm.role) { if (vm.payoutState is Load.Idle) vm.loadPayouts() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("payouts")) {
        item { Heading("Payout") }
        when (val state = vm.payoutState) {
            Load.Idle, Load.Loading -> item { LoadingCard("Loading your payouts…") }
            is Load.Failed -> item { ErrorCard(state.message) { vm.loadPayouts() } }
            is Load.Ready -> {
                val payouts = state.value
                if (payouts.isEmpty()) {
                    item { EmptyState(PaymentsIcon, "No payouts yet", "Payouts for the classes you teach appear here once they are paid.") }
                } else {
                    item {
                        SummaryCard("Total paid", money(payouts.sumOf { it.amount }), "${payouts.size} payout${if (payouts.size == 1) "" else "s"}")
                    }
                    items(payouts, key = { it.source + "-" + it.id }) { PayoutCard(it) }
                }
            }
        }
    }
}

@Composable
private fun PayoutCard(payout: Payout) {
    InfoCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(payout.title.ifEmpty { "Class" }, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(money(payout.amount), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalLmsColors.current.success)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (payout.startDate.isNotEmpty()) LabeledValue("Class", dateRange(payout.startDate, payout.endDate))
            if (payout.paymentDate.isNotEmpty()) LabeledValue("Paid", prettyDay(payout.paymentDate))
            if (payout.learners > 0) LabeledValue("Learners", "${payout.learners}")
            if (payout.courseCode.isNotEmpty()) LabeledValue("Course", payout.courseCode)
        }
    }
}

// ---------------------------------------------------------------- tabs waiting for their data
// Each tab below is its own screen so a later phase only swaps the body: add the state to
// LmsViewModel and the call to LmsApi (as PayoutScreen does), then replace ComingSoon with the
// list. The existing web routes for these read a user id from the query string and must not be
// called from the app; the tabs stay empty until the secure endpoints exist.

@Composable
private fun ComingSoon(tag: String, heading: String, icon: ImageVector, text: String) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag(tag)) {
        item { Heading(heading) }
        item { EmptyState(icon, "Coming soon", text) }
    }
}

/** Trainer: hours taught, by month and by class. */
@Composable
fun TrainingHoursScreen(@Suppress("UNUSED_PARAMETER") vm: LmsViewModel) {
    ComingSoon("hours", "Training hours", ClockIcon, "Your training hours for the classes you teach will appear here.")
}

/** Learner: certificates earned, to view and download. */
@Composable
fun CertificatesScreen(@Suppress("UNUSED_PARAMETER") vm: LmsViewModel) {
    ComingSoon("certificates", "Certificates", CertificateIcon, "Your course certificates will appear here once they are issued.")
}

/** Learner: invoices and payments. */
@Composable
fun BillingScreen(@Suppress("UNUSED_PARAMETER") vm: LmsViewModel) {
    ComingSoon("billing", "Billing", CreditCardIcon, "Your invoices and payment history will appear here.")
}
