package com.ritmo.treinos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritmo.treinos.online.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun OnlineStatus(vm: RitmoViewModel) {
    val busy by vm.onlineBusy.collectAsStateWithLifecycle()
    val message by vm.onlineMessage.collectAsStateWithLifecycle()
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message!=null) Text(message!!,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
}
@Composable fun RankingScreen(vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    var period by rememberSaveable { mutableStateOf("weekly") }
    var limit by rememberSaveable { mutableIntStateOf(10) }
    var board by remember { mutableStateOf(RankingBoard()) }
    var loading by remember { mutableStateOf(false) }
    var stale by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val account by vm.online.account.collectAsStateWithLifecycle()
    val profile by vm.online.profile.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    suspend fun refresh() {
        if(loading) return
        loading=true
        try { board=vm.online.board(period,limit); stale=false; error=null }
        catch(e: Exception) { if(e is CancellationException) throw e; stale=true; error=e.message ?: "Sem conexão. Tente novamente." }
        finally { loading=false }
    }
    LaunchedEffect(period,limit,account?.id) {
        board=vm.online.cachedBoard(period,limit) ?: RankingBoard(); stale=true
        while(true) { refresh(); delay(30_000) }
    }
    Page {
        item {
            PageTitle("Ranking global","Constância no seu ritmo. Competição recreativa.",back)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(period=="weekly",{ period="weekly" },{ Text("Semana") })
                FilterChip(period=="monthly",{ period="monthly" },{ Text("Mês") })
                FilterChip(period=="all",{ period="all" },{ Text("Geral") })
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(limit==10,{limit=10},{Text("Top 10")}); FilterChip(limit==100,{limit=100},{Text("Top 100")})
                Spacer(Modifier.weight(1f)); IconButton({scope.launch { refresh() }},enabled=!loading) { Icon(Icons.Default.Refresh,"Atualizar ranking") }
            }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(stale && board.entries.isNotEmpty()) Text("Dados salvos • aguardando atualização",color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
        }
        item {
            if(account==null) EmptyState("Entre para participar","Seus treinos continuam funcionando offline. A conta é necessária somente para o ranking.")
            else if(profile?.enabled!=true) EmptyState("Seu perfil está privado","Ative sua participação para aparecer no ranking e registrar XP em novos treinos.")
            else if(board.me!=null) {
                val me=board.me!!
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("#${me.position}",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) { Text("Sua posição",style=MaterialTheme.typography.labelLarge); Text(me.nickname,style=MaterialTheme.typography.titleMedium); Text("${me.xp} XP • nível ${me.level}") }
                    }
                }
            }
            TextButton({go("profile")}) { Text(if(account==null) "Entrar ou criar conta" else "Meu perfil e XP") }
        }
        if(board.entries.isNotEmpty()) {
            item { Section("No pódio"); Spacer(Modifier.height(12.dp)); PodiumCard(board.entries[0],true) }
            if(board.entries.size>1) item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { board.entries.drop(1).take(2).forEach { entry -> Box(Modifier.weight(1f)) { PodiumCard(entry,false) } } } }
            item { Section("Classificação") }
            items(board.entries.drop(3),key={it.position}) { entry -> RankingRow(entry) }
        } else if(!loading && error==null) item { EmptyState("O ranking começa com você","Ative sua participação no perfil. O primeiro treino válido de cada dia rende 100 XP.") }
        item { Text("Semana: segunda a domingo. Dia e mês seguem UTC. O nível usa seu XP geral. Em caso de empate, o apelido define a ordem.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun PodiumCard(entry: RankingEntry,first: Boolean) {
    val gradient=Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.surfaceContainerHigh))
    Column(Modifier.fillMaxWidth().background(gradient,RoundedCornerShape(24.dp)).padding(if(first) 24.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(Icons.Default.EmojiEvents,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(if(first) 40.dp else 28.dp))
        Text("#${entry.position}",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(entry.nickname,style=MaterialTheme.typography.titleMedium)
        Text("${entry.xp} XP",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        Text("Nível ${entry.level}",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun RankingRow(entry: RankingEntry) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("#${entry.position}",color=MaterialTheme.colorScheme.primary,modifier=Modifier.widthIn(min=40.dp))
        Column(Modifier.weight(1f)) { Text(entry.nickname,style=MaterialTheme.typography.titleMedium); Text("Nível ${entry.level}",style=MaterialTheme.typography.bodySmall) }
        Text("${entry.xp} XP",fontWeight=FontWeight.Bold)
    } }
}
@Composable fun AccountScreen(vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val account by vm.online.account.collectAsStateWithLifecycle()
    val recovery by vm.online.recovery.collectAsStateWithLifecycle()
    val busy by vm.onlineBusy.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signup by rememberSaveable { mutableStateOf(false) }
    Page {
        item { PageTitle(if(recovery) "Nova senha" else if(account==null) "Sua conta Ritmo" else "Conta", "Treine offline. Entre quando quiser participar.",back); OnlineStatus(vm) }
        if(account==null || recovery) {
            if(!recovery) item { OutlinedTextField(email,{email=it},label={Text("E-mail")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,modifier=Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(password,{password=it},label={Text(if(recovery) "Nova senha" else "Senha")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Use pelo menos 8 caracteres para criar ou redefinir a senha.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
            }
            item {
                Button(onClick={ if(recovery) vm.resetPassword(password) else if(signup) vm.signup(email,password) else vm.login(email,password); password="" },enabled=!busy&&vm.online.configured,modifier=Modifier.fillMaxWidth().height(54.dp)) { Text(if(recovery) "Salvar nova senha" else if(signup) "Criar conta" else "Entrar") }
                if(!recovery) {
                    TextButton({signup=!signup},enabled=!busy) { Text(if(signup) "Já tenho conta" else "Criar uma conta") }
                    TextButton({vm.recoverAccount(email)},enabled=!busy&&vm.online.configured) { Text("Esqueci minha senha") }
                }
            }
            if(!vm.online.configured) item { EmptyState("Ranking aguardando configuração","Esta compilação ainda não está conectada ao Supabase. Seus treinos offline estão disponíveis.") }
        } else item {
            Text(account!!.email,style=MaterialTheme.typography.titleMedium)
            TextButton({go("profile")}) { Text("Meu perfil") }
            TextButton({go("privacy")}) { Text("Conta e privacidade") }
            OutlinedButton({vm.logout()},enabled=!busy) { Text("Sair da conta") }
        }
        item { Text("Seu e-mail não aparece no ranking. Cargas, exercícios e anotações ficam neste aparelho.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun ProfileScreen(vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val account by vm.online.account.collectAsStateWithLifecycle()
    val profile by vm.online.profile.collectAsStateWithLifecycle()
    val summary by vm.online.summary.collectAsStateWithLifecycle()
    val busy by vm.onlineBusy.collectAsStateWithLifecycle()
    val queue by vm.rankingEvents.collectAsStateWithLifecycle()
    var nickname by rememberSaveable(account?.id,profile?.nickname) { mutableStateOf(profile?.nickname.orEmpty()) }
    var enabled by rememberSaveable(account?.id,profile?.enabled) { mutableStateOf(profile?.enabled ?: false) }
    LaunchedEffect(account?.id) { if(account!=null) vm.refreshOnline() }
    Page {
        item { PageTitle("Meu perfil","Você escolhe quando participar.",back); OnlineStatus(vm) }
        if(account==null) item { EmptyState("Continue no seu ritmo","O cadastro é opcional. Entre para criar seu apelido e participar do ranking."); Button({go("account")},modifier=Modifier.fillMaxWidth()) { Text("Entrar ou criar conta") } }
        else {
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { MetricCard("Nível ${summary.level}","seu nível",Modifier.weight(1f)); MetricCard("${summary.total}","XP geral",Modifier.weight(1f)) }
            }
            item {
                OutlinedTextField(nickname,{nickname=it},label={Text("Apelido público")},supportingText={Text("3 a 24 letras, números ou _")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("Participar do ranking",modifier=Modifier.weight(1f)); Switch(enabled,{enabled=it},enabled=!busy) }
                Text("Ao ativar, novos treinos válidos poderão gerar XP. O histórico anterior não será enviado.",style=MaterialTheme.typography.bodySmall)
                Button({vm.saveOnlineProfile(nickname.trim(),enabled)},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Text("Salvar perfil") }
            }
            item {
                Section("Sincronização")
                val pending=queue.count {it.status=="pending"}; val rejected=queue.count {it.status=="rejected"}
                Text("$pending pendentes • ${queue.count {it.status=="synced"}} sincronizados • $rejected não pontuados")
                val error by vm.online.syncError.collectAsStateWithLifecycle()
                if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pendências são enviadas com internet e participação ativa, por até 7 dias. Não são transferidas para outra conta.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton({vm.syncRanking()},enabled=!busy&&profile?.enabled==true) { Text("Sincronizar agora") }
            }
            items(queue.filter { it.status=="rejected" }.takeLast(3),key={it.eventId}) { event -> Text("${date(event.completedAt)} • ${event.error ?: "Não elegível"}",style=MaterialTheme.typography.bodySmall) }
            item { TextButton({go("achievements")}) { Text("XP e conquistas") }; TextButton({go("ranking")}) { Text("Ver ranking global") }; TextButton({go("privacy")}) { Text("Conta e privacidade") } }
        }
    }
}
@Composable fun AchievementsScreen(vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val account by vm.online.account.collectAsStateWithLifecycle()
    val summary by vm.online.summary.collectAsStateWithLifecycle()
    Page {
        item { PageTitle("XP e conquistas","Pequenos passos, sem pressa.",back) }
        if(account==null) item { Button({go("account")}) { Text("Entrar para ver meu XP") } }
        else {
            item { MetricCard("${summary.total} XP","Nível ${summary.level} • ${summary.today}/125 XP hoje",Modifier.fillMaxWidth()) }
            item { LinearProgressIndicator(progress={ (summary.total%500)/500f },modifier=Modifier.fillMaxWidth()); Text("${500-summary.total%500} XP para o próximo nível",style=MaterialTheme.typography.bodySmall) }
            items(summary.badges) { (name,earned) -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Icon(if(earned) Icons.Default.EmojiEvents else Icons.Default.Lock,null,tint=if(earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Text(name); if(earned) Text("✓") } } }
        }
        item { Section("Como funciona"); Text("Primeiro treino válido do dia: 100 XP. Segundo: 25 XP. Demais: 0 XP. Limite: 125 XP por dia, com virada às 00h UTC (21h em Brasília).") }
        item { Text("Um treino válido tem ao menos uma série concluída, dura entre 1 minuto e 24 horas e começa após ativar o ranking. Apenas os horários são enviados. Os pontos são confirmados pelo servidor.") }
        item { Text("Os registros são declarados pelos participantes. O ranking é recreativo. Treinar mais, levantar mais carga ou mudar características físicas não dá bônus.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun PrivacyScreen(vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val account by vm.online.account.collectAsStateWithLifecycle()
    val profile by vm.online.profile.collectAsStateWithLifecycle()
    val busy by vm.onlineBusy.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Page {
        item { PageTitle("Conta e privacidade","Controle sua participação e seus dados.",back); OnlineStatus(vm) }
        item { Text("No ranking público aparecem somente apelido, posição, XP e nível. O servidor guarda os horários e identificadores dos eventos para evitar duplicidade. Seu histórico detalhado fica local.") }
        if(account==null) item { Button({go("account")}) { Text("Entrar ou criar conta") } }
        else {
            item {
                Text("${if(profile?.enabled==true) "Participação ativa" else "Participação desativada"}",style=MaterialTheme.typography.titleMedium)
                OutlinedButton({vm.saveOnlineProfile(profile!!.nickname,false)},enabled=!busy&&profile?.enabled==true) { Text("Desativar participação") }
                Text("Desativar oculta seu perfil do ranking e pausa o envio de pendências. Ao reativar, somente treinos iniciados após essa ativação serão elegíveis.",style=MaterialTheme.typography.bodySmall)
            }
            item { OutlinedButton({vm.logout()},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Sair da conta") }; Text("Sair não apaga treinos nem transfere pendências para outra conta.",style=MaterialTheme.typography.bodySmall) }
            item { Button({confirmDelete=true},enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error),modifier=Modifier.fillMaxWidth()) { Text("Excluir conta e dados online") }; Text("Seus treinos neste aparelho permanecem. A exclusão online precisa de conexão e é definitiva.",style=MaterialTheme.typography.bodySmall) }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Excluir conta?")},text={Text("Seu perfil, eventos e XP online serão excluídos definitivamente. Seus treinos locais serão preservados.")},confirmButton={TextButton({confirmDelete=false;vm.deleteAccount()}) {Text("Excluir conta",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton({confirmDelete=false}) {Text("Cancelar")}})
}
