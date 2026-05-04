package com.georgeci.moneysurfer.domain.constants

// Stable, fixed UUID for the local "default" user row. Used as a fallback ownerId
// when there is no signed-in user (e.g. anonymous flows, guest sign-in).
const val PREFILLED_DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"
