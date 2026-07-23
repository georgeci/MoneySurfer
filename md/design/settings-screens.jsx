// Settings flow — composed from settings-components primitives.
//
//   SettingsHubScreen      — main settings page (entry point)
//   AppearanceScreen       — theme + accent + dynamic colors
//   BackupSyncScreen       — sign-in cloud account, sync state, export
//   AboutLegalScreen       — version, licenses, terms, privacy, contact
//   PreferencesScreen      — currency, week start, number format, hour
//
// All screens reuse the SettingsRow / SettingsGroup shell so the rhythm
// is identical across pages: large top app bar, optional hero card,
// sections with primary-tinted titles, rows with leading icon tile.

// ── Settings hub ─────────────────────────────────────────────
function SettingsHubScreen({ scheme, dark, seedName, dynamicOn }) {
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Settings" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Profile hero — primary-container card with avatar + name + email */}
        <div style={{ padding: '4px 16px 12px' }}>
          <NameBlock scheme={scheme}
            name="Kasia Nowak"
            email="kasia@finyard.com"
            initial="K"/>
        </div>

        <SettingsGroup title="Workspace" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Users s={20}/>}
            title="Change workspace"
            supporting="Currently: Family"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Members & permissions"
            supporting="4 members"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Mail s={20}/>}
            title="Invitations"
            supporting={<PendingBadge scheme={scheme}>2 pending</PendingBadge>}
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Personalization" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Palette s={20}/>}
            title="Appearance"
            supporting={dynamicOn
              ? `Dynamic · ${dark ? 'Dark' : 'Light'}`
              : `Seed: ${seedName} · ${dark ? 'Dark' : 'Light'}`}
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Settings s={20}/>}
            title="Preferences"
            supporting="EUR · Monday · 24h · 1,234.56"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Bell s={20}/>}
            title="Notifications"
            supporting="Budget alerts, weekly summary"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Globe s={20}/>}
            title="Language"
            trailing={<div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <SettingsValuePill scheme={scheme}>English</SettingsValuePill>
              <SettingsChevron scheme={scheme}/>
            </div>}/>
        </SettingsGroup>

        <SettingsGroup title="Data" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Sync s={20}/>}
            title="Sync"
            supporting="Synced 4 minutes ago · iCloud"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Cloud s={20}/>}
            title="Backup"
            supporting="Last backup: today, 14:02"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Receipt s={20}/>}
            title="Export to CSV"
            supporting="All transactions, all time"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Shield s={20}/>}
            title="Security"
            supporting="App lock, biometrics"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Help & info" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Info s={20}/>}
            title="About & legal"
            supporting="v1.4.0"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Mail s={20}/>}
            title="Send feedback"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <div style={{ height: 8 }}/>
        <SettingsRow scheme={scheme}
          icon={<I.Logout s={20}/>}
          title="Log out"
          danger/>

        <div style={{
          ...type('bodySmall'), color: scheme.onSurfaceVariant,
          textAlign: 'center', padding: 24,
        }}>
          Ledger · v1.4.0 · build 2025.04
        </div>
      </div>
    </div>
  );
}

// ── Appearance ────────────────────────────────────────────────
// Theme (System / Light / Dark) — radio list to make the choice visible
// at a glance, vs. a segmented control. Accent color — full grid of
// seed swatches. Dynamic colors — switch.
function AppearanceScreen({ scheme, dark, seedName, dynamicOn }) {
  const themeMode = dark ? 'dark' : 'light';
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Appearance" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Live preview tile */}
        <div style={{ padding: '4px 16px 16px' }}>
          <LiveSchemePreview scheme={scheme}/>
        </div>

        <SettingsGroup title="Theme" scheme={scheme}>
          {[
            { id: 'system', label: 'Follow system', supporting: 'Match device dark mode', icon: <I.Sparkle s={20}/> },
            { id: 'light',  label: 'Light',         supporting: 'Bright surfaces',         icon: <I.Sun s={20}/> },
            { id: 'dark',   label: 'Dark',          supporting: 'Dim surfaces, less glare', icon: <I.Moon s={20}/> },
          ].map(o => (
            <SettingsRow key={o.id} scheme={scheme}
              icon={o.icon}
              title={o.label}
              supporting={o.supporting}
              trailing={<SettingsRadio scheme={scheme} on={o.id === themeMode}/>}/>
          ))}
        </SettingsGroup>

        <SettingsGroup title="Color source" scheme={scheme}
          footnote={dynamicOn
            ? "Wallpaper-derived palette is in use. Turn off to choose a fixed accent below."
            : "Use a fixed accent, or match your wallpaper automatically."}>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Dynamic colors"
            supporting="Match wallpaper palette (Android 12+)"
            trailing={<SettingsSwitch scheme={scheme} on={dynamicOn}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Accent color" scheme={scheme}
          footnote={dynamicOn
            ? "Disabled while Dynamic colors is on."
            : "Colors buttons, highlights, and chart accents."}>
          <div style={{ padding: '4px 16px 12px' }}>
            <ColorPicker scheme={scheme}
              seeds={SEEDS}
              value={seedName}
              disabled={dynamicOn}/>
          </div>
        </SettingsGroup>

        <SettingsGroup title="System" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Eye s={20}/>}
            title="Reduce motion"
            supporting="Cross-fade instead of slide"
            trailing={<SettingsSwitch scheme={scheme} on={false}/>}/>
        </SettingsGroup>
        <div style={{ height: 24 }}/>
      </div>
    </div>
  );
}

// ── Sync ──────────────────────────────────────────────────────
// Live state of cross-device sync. Status card up top + cloud account
// selection + behaviour toggles. Backup is its own separate screen.
function SyncScreen({ scheme, syncing }) {
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Sync" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Sync status card */}
        <div style={{ padding: '4px 16px 16px' }}>
          <SyncStatusCard scheme={scheme} syncing={syncing}/>
        </div>

        <SettingsGroup title="Cloud account" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Cloud s={20}/>}
            title="iCloud"
            supporting="kasia@finyard.com · 18 MB used"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sync s={20}/>}
            title="Auto-sync"
            supporting="Keeps every device in sync"
            trailing={<SettingsSwitch scheme={scheme} on={true}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Network" scheme={scheme}
          footnote="Cellular sync uses your data plan. Off by default to save data.">
          <SettingsRow scheme={scheme}
            icon={<I.Globe s={20}/>}
            title="Sync over cellular"
            supporting="Off — Wi-Fi only"
            trailing={<SettingsSwitch scheme={scheme} on={false}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Bolt s={20}/>}
            title="Sync in background"
            supporting="Push when app is closed"
            trailing={<SettingsSwitch scheme={scheme} on={true}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Devices" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="iPhone 15 Pro"
            supporting="This device · last seen now"
            trailing={<SettingsValuePill scheme={scheme}>Active</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="MacBook Air"
            supporting="Synced 2 hours ago"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="iPad"
            supporting="Synced 3 days ago"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <div style={{ height: 8 }}/>
        <SettingsRow scheme={scheme}
          icon={<I.Sync s={20}/>}
          title="Force sync now"
          tone={{ bg: scheme.primaryContainer }}/>
        <div style={{ height: 24 }}/>
      </div>
    </div>
  );
}

// ── Backup ────────────────────────────────────────────────────
// Backup is for safety + portability — a snapshot of your data, not
// the live sync stream. Dedicated screen so destructive options
// (Delete cloud backup, Restore) are not next to live-sync toggles.
function BackupScreen({ scheme }) {
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Backup" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Last backup hero */}
        <div style={{ padding: '4px 16px 16px' }}>
          <StatusHeroCard scheme={scheme}
            tone="primary"
            icon={<I.Cloud/>}
            title="Last backup · today"
            supporting="14:02 · 18 MB · 2,341 transactions"/>
        </div>

        <SettingsGroup title="Schedule" scheme={scheme}
          footnote="Backups are end-to-end encrypted with your account key.">
          <SettingsRow scheme={scheme}
            icon={<I.Calendar s={20}/>}
            title="Backup frequency"
            trailing={<SettingsValuePill scheme={scheme}>Daily</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Shield s={20}/>}
            title="Encryption"
            supporting="AES-256, end-to-end"
            trailing={<SettingsValuePill scheme={scheme}>On</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Cloud s={20}/>}
            title="Storage location"
            trailing={<SettingsValuePill scheme={scheme}>iCloud</SettingsValuePill>}/>
        </SettingsGroup>

        <SettingsGroup title="Manual" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Cloud s={20}/>}
            title="Back up now"
            supporting="Force a fresh snapshot"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Download s={20}/>}
            title="Download a copy"
            supporting=".ledger archive · 18 MB"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Restore" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Sync s={20}/>}
            title="Restore from backup"
            supporting="Replaces current data"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <div style={{ height: 8 }}/>
        <SettingsRow scheme={scheme}
          icon={<I.Trash s={20}/>}
          title="Delete cloud backup"
          danger
          supporting="Removes all backups from iCloud"/>
        <div style={{ height: 32 }}/>
      </div>
    </div>
  );
}

// Legacy combined name kept for back-compat — alias to SyncScreen.
const BackupSyncScreen = SyncScreen;

// ── About & Legal ─────────────────────────────────────────────
function AboutLegalScreen({ scheme }) {
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="About & legal" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* App identity hero */}
        <div style={{
          padding: '8px 24px 28px',
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', gap: 10,
        }}>
          <div style={{
            width: 72, height: 72, borderRadius: 20,
            background: scheme.primary, color: scheme.onPrimary,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            ...type('headlineSmall'),
            boxShadow: '0 6px 20px rgba(0,0,0,0.12)',
          }}>L</div>
          <div style={{ ...type('titleLarge'), color: scheme.onSurface }}>Ledger</div>
          <div style={{ ...type('bodyMedium'), color: scheme.onSurfaceVariant }}>
            v1.4.0 · build 2025.04 · iOS / Android
          </div>
          <div style={{
            display: 'flex', gap: 8, marginTop: 6,
            ...type('labelMedium'), color: scheme.onSurfaceVariant,
          }}>
            <div style={{
              padding: '4px 10px', borderRadius: 999,
              background: scheme.surfaceContainerHighest,
            }}>Made by Georgy Balabaichkin</div>
            <div style={{
              padding: '4px 10px', borderRadius: 999,
              background: scheme.surfaceContainerHighest,
            }}>MIT License</div>
          </div>
        </div>

        <SettingsGroup title="Legal" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Receipt s={20}/>}
            title="Terms of service"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Shield s={20}/>}
            title="Privacy policy"
            supporting="What we collect & why"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Code s={20}/>}
            title="Open-source licenses"
            supporting="34 packages"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Help" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Info s={20}/>}
            title="Help center"
            supporting="Guides & FAQ"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Mail s={20}/>}
            title="Contact support"
            supporting="hello@finyard.com"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Rate Ledger"
            supporting="On the App Store"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <SettingsGroup title="System" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Globe s={20}/>}
            title="Region & language"
            trailing={<SettingsValuePill scheme={scheme}>English (UK)</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Code s={20}/>}
            title="Diagnostic info"
            supporting="Tap to copy"
            trailing={<SettingsChevron scheme={scheme}/>}/>
        </SettingsGroup>

        <div style={{
          ...type('bodySmall'), color: scheme.onSurfaceVariant,
          textAlign: 'center', padding: '20px 32px 28px',
          lineHeight: 1.5,
        }}>
          © 2025 Georgy Balabaichkin. Released under the MIT License.<br/>
          Ledger is a personal-finance tracker, not a bank or money-transmission service.
        </div>
      </div>
    </div>
  );
}

// ── Preferences ───────────────────────────────────────────────
function PreferencesScreen({ scheme }) {
  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Preferences" large leading={<I.Back s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <SettingsGroup title="Language & region" scheme={scheme}
          footnote="Used across menus, dates, and number formatting.">
          <SettingsRow scheme={scheme}
            icon={<I.Globe s={20}/>}
            title="App language"
            supporting="Overrides system language for this app"
            trailing={<SettingsValuePill scheme={scheme}>English</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Region"
            supporting="Affects currency and date defaults"
            trailing={<SettingsValuePill scheme={scheme}>Poland</SettingsValuePill>}/>
        </SettingsGroup>

        <SettingsGroup title="Money" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Wallet s={20}/>}
            title="Default currency"
            supporting="Used for new accounts and totals"
            trailing={<SettingsValuePill scheme={scheme}>EUR · €</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Code s={20}/>}
            title="Number format"
            trailing={<SettingsValuePill scheme={scheme}>1,234.56</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Eye s={20}/>}
            title="Hide amounts on home screen"
            supporting="Show ••• until tapped"
            trailing={<SettingsSwitch scheme={scheme} on={false}/>}/>
        </SettingsGroup>

        <SettingsGroup title="Calendar" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Calendar s={20}/>}
            title="Week starts on"
            trailing={<SettingsValuePill scheme={scheme}>Monday</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Clock s={20}/>}
            title="Time format"
            trailing={<SettingsValuePill scheme={scheme}>24-hour</SettingsValuePill>}/>
        </SettingsGroup>

        <SettingsGroup title="Behavior" scheme={scheme}
          footnote="These tune how the app feels day-to-day.">
          <SettingsRow scheme={scheme}
            icon={<I.Plus s={20}/>}
            title="Default new-transaction type"
            trailing={<SettingsValuePill scheme={scheme}>Expense</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Auto-categorize"
            supporting="Suggest a category from merchant"
            trailing={<SettingsSwitch scheme={scheme} on={true}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Bell s={20}/>}
            title="Round-up savings"
            supporting="Off — round purchases up to nearest €1"
            trailing={<SettingsSwitch scheme={scheme} on={false}/>}/>
        </SettingsGroup>
        <div style={{ height: 24 }}/>
      </div>
    </div>
  );
}

// ── User profile ──────────────────────────────────────────────
// Personal account card: who you are, sign-in identity, security
// posture, active sessions. Lives one tap below the hero NameBlock
// in the Settings hub. Workspace-level controls (Members, billing,
// transfer ownership) intentionally live elsewhere — this screen is
// strictly about the human, not the workspace.
function UserProfileScreen({ scheme }) {
  const name = 'Kasia Nowak';
  const email = 'kasia@finyard.com';
  const initial = 'K';

  return (
    <div style={{
      background: scheme.surface, color: scheme.onSurface,
      height: '100%', display: 'flex', flexDirection: 'column',
      fontFamily: '"Roboto Flex", Roboto, system-ui, sans-serif',
    }}>
      <TopBar scheme={scheme} title="Profile"
        leading={<I.Back s={22} c={scheme.onSurface}/>}
        trailing={<I.Edit s={22} c={scheme.onSurface}/>}/>

      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Hero — large avatar centered, name + email below, with a
            small "change photo" affordance overlapping the avatar.
            Uses primaryContainer so the card carries the seed accent. */}
        <div style={{ padding: '8px 16px 20px' }}>
          <div style={{
            background: scheme.primaryContainer, color: scheme.onPrimaryContainer,
            borderRadius: 28, padding: '28px 20px 20px',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12,
            boxShadow: '0 2px 6px rgba(16,24,40,0.05), 0 1px 2px rgba(16,24,40,0.04)',
          }}>
            <div style={{ position: 'relative' }}>
              <div style={{
                width: 96, height: 96, borderRadius: 48,
                background: scheme.primary, color: scheme.onPrimary,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 40, fontWeight: 500, letterSpacing: -0.5,
              }}>{initial}</div>
              <div style={{
                position: 'absolute', right: -2, bottom: -2,
                width: 32, height: 32, borderRadius: 16,
                background: scheme.surface, color: scheme.onSurface,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: '0 2px 6px rgba(16,24,40,0.18)',
              }}>
                <I.Edit s={16}/>
              </div>
            </div>
            <div style={{ textAlign: 'center', maxWidth: '100%' }}>
              <div style={{ ...type('headlineSmall'), letterSpacing: -0.3, whiteSpace: 'nowrap' }}>{name}</div>
              <div style={{ ...type('bodyMedium'), opacity: 0.85, marginTop: 2, whiteSpace: 'nowrap' }}>{email}</div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6,
              background: scheme.surface, color: scheme.onSurface,
              padding: '6px 12px', borderRadius: 999, marginTop: 4,
              whiteSpace: 'nowrap',
              ...type('labelMedium'),
            }}>
              <I.Users s={14} c={scheme.onSurface}/>
              <span>Owner · Family</span>
            </div>
          </div>
        </div>

        {/* Account identity */}
        <SettingsGroup title="Account" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Edit s={20}/>}
            title="Display name"
            trailing={<div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <SettingsValuePill scheme={scheme}>{name}</SettingsValuePill>
              <SettingsChevron scheme={scheme}/>
            </div>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Mail s={20}/>}
            title="Email"
            supporting={email}
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Globe s={20}/>}
            title="Time zone"
            trailing={<div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <SettingsValuePill scheme={scheme}>Europe/Warsaw</SettingsValuePill>
              <SettingsChevron scheme={scheme}/>
            </div>}/>
        </SettingsGroup>

        {/* Security */}
        <SettingsGroup title="Security" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Shield s={20}/>}
            title="Change password"
            supporting="Last changed 3 months ago"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Sparkle s={20}/>}
            title="Two-factor authentication"
            supporting={<PendingBadge scheme={scheme}>Off</PendingBadge>}
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Bell s={20}/>}
            title="Sign-in alerts"
            supporting="Email me on a new device"
            trailing={<SettingsSwitch on scheme={scheme}/>}/>
        </SettingsGroup>

        {/* Active sessions — current device + one other, with a
            "sign out elsewhere" affordance for quick clean-up */}
        <SettingsGroup title="Active sessions" scheme={scheme}
          footnote="If you don't recognize a device, sign it out and change your password.">
          <SettingsRow scheme={scheme}
            icon={<I.Phone s={20}/>}
            title="iPhone 15 · Warsaw"
            supporting="This device · active now"
            trailing={<SettingsValuePill scheme={scheme}>You</SettingsValuePill>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Code s={20}/>}
            title="MacBook Pro · Chrome"
            supporting="Last active yesterday, 19:42"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Logout s={20}/>}
            title="Sign out other devices"
            danger/>
        </SettingsGroup>

        {/* Danger zone — visually quieter, but still card-style for
            consistency. Delete is destructive + final, hence its own
            group at the very bottom. */}
        <SettingsGroup title="Danger zone" scheme={scheme}>
          <SettingsRow scheme={scheme}
            icon={<I.Receipt s={20}/>}
            title="Download my data"
            supporting="JSON archive of your account"
            trailing={<SettingsChevron scheme={scheme}/>}/>
          <SettingsRow scheme={scheme}
            icon={<I.Trash s={20}/>}
            title="Delete account"
            supporting="Permanent · cannot be undone"
            danger/>
        </SettingsGroup>

        <div style={{ height: 24 }}/>
      </div>
    </div>
  );
}

Object.assign(window, {
  SettingsHubScreen, AppearanceScreen, SyncScreen, BackupScreen, BackupSyncScreen,
  AboutLegalScreen, PreferencesScreen, UserProfileScreen,
});
