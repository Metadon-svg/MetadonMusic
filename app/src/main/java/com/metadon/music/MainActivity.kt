@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTabUI(vm: MusicViewModel) {
    var q by remember { mutableStateOf("") }
    val sugs by vm.suggestions.collectAsState()
    val res by vm.searchResults.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { q = it; vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Искать...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1A1A), unfocusedContainerColor = Color(0xFF1A1A1A), focusedTextColor = Color.White),
            trailingIcon = { if(q.isNotEmpty()) Icon(Icons.Default.Close, null, Modifier.clickable { q = "" }) }
        )
        
        LazyColumn {
            // ОТОБРАЖЕНИЕ ПОДСКАЗОК КАК В СКРИНШОТЕ
            items(sugs) { text ->
                Row(Modifier.fillMaxWidth().clickable { q = text; vm.search(text) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text, color = Color.White)
                    Icon(Icons.Default.ArrowOutward, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
            items(res) { TrackRow(it) { vm.play(it) } }
        }
    }
}
