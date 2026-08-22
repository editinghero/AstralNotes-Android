package com.astralquarks.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.res.painterResource
import com.astralquarks.notes.R
import com.astralquarks.notes.ui.theme.GeminiSparklePink

enum class DrawerDestination {
    NOTES,
    PINNED,
    TAGS,
    LOCKED_VAULT,
    AI_ASSISTANT,
    DASHBOARD,
    ARCHIVE,
    TRASH,
    SETTINGS
}

@Composable
fun ExpressiveDrawerContent(
    currentDestination: DrawerDestination,
    onSelectDestination: (DrawerDestination) -> Unit,
    lockedCount: Int,
    activeNotesCount: Int,
    userDisplayName: String?,
    isSignedIn: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header with custom pencil logo and proper spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pencil),
                        contentDescription = "AstralNotes Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "AstralNotes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = if (isSignedIn) (userDisplayName ?: "Signed in with Cloud") else "Local Offline Vault",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Destination Items
            DrawerNavItem(
                label = "Notes",
                icon = Icons.Default.Description,
                badgeCount = activeNotesCount,
                isSelected = currentDestination == DrawerDestination.NOTES,
                onClick = { onSelectDestination(DrawerDestination.NOTES) },
                testTag = "drawer_nav_notes"
            )

            DrawerNavItem(
                label = "Pinned Notes",
                icon = Icons.Default.PushPin,
                isSelected = currentDestination == DrawerDestination.PINNED,
                onClick = { onSelectDestination(DrawerDestination.PINNED) },
                testTag = "drawer_nav_pinned"
            )

            DrawerNavItem(
                label = "Tags & Labels",
                icon = Icons.Default.Label,
                isSelected = currentDestination == DrawerDestination.TAGS,
                onClick = { onSelectDestination(DrawerDestination.TAGS) },
                testTag = "drawer_nav_tags"
            )

            // Locked Vault Section (Badge count omitted to protect privacy)
            DrawerNavItem(
                label = "Private Vault",
                icon = Icons.Default.Lock,
                badgeCount = null,
                isSelected = currentDestination == DrawerDestination.LOCKED_VAULT,
                onClick = { onSelectDestination(DrawerDestination.LOCKED_VAULT) },
                testTag = "drawer_nav_locked_vault"
            )

            DrawerNavItem(
                label = "Gemini AI Assistant",
                icon = Icons.Default.AutoAwesome,
                isSelected = currentDestination == DrawerDestination.AI_ASSISTANT,
                onClick = { onSelectDestination(DrawerDestination.AI_ASSISTANT) },
                testTag = "drawer_nav_ai"
            )

            DrawerNavItem(
                label = "Dashboard & Insights",
                icon = Icons.Default.BarChart,
                isSelected = currentDestination == DrawerDestination.DASHBOARD,
                onClick = { onSelectDestination(DrawerDestination.DASHBOARD) },
                testTag = "drawer_nav_dashboard"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            DrawerNavItem(
                label = "Archive",
                icon = Icons.Default.Archive,
                isSelected = currentDestination == DrawerDestination.ARCHIVE,
                onClick = { onSelectDestination(DrawerDestination.ARCHIVE) },
                testTag = "drawer_nav_archive"
            )

            DrawerNavItem(
                label = "Trash",
                icon = Icons.Default.Delete,
                isSelected = currentDestination == DrawerDestination.TRASH,
                onClick = { onSelectDestination(DrawerDestination.TRASH) },
                testTag = "drawer_nav_trash"
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            DrawerNavItem(
                label = "Settings & Vault Security",
                icon = Icons.Default.Settings,
                isSelected = currentDestination == DrawerDestination.SETTINGS,
                onClick = { onSelectDestination(DrawerDestination.SETTINGS) },
                testTag = "drawer_nav_settings"
            )
        }
    }
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeCount: Int? = null,
    badgeColor: Color? = null,
    iconTint: Color? = null
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp
                )
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        badge = {
            if (badgeCount != null && badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .padding(vertical = 3.dp, horizontal = 4.dp)
            .testTag(testTag)
    )
}
