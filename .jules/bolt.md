## 2024-05-24 - Compose LazyColumn Performance
**Learning:** Found multiple instances of `LazyColumn` in `AiAssistantBottomSheet.kt` iterating over lists without providing a `key`. This causes unnecessary recompositions of all list items in Jetpack Compose when the underlying dataset changes (e.g., chat messages update).
**Action:** Added stable `key` identifiers to `items(chatMessages)`, `items(filteredHistory)`, and `items(allNotes)` to allow Jetpack Compose to efficiently track changes and avoid full list re-renders. Ensure `key` parameters are used for all dynamic lists in the future.
