# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin, Jetpack Compose and Material 3 in a single Android app module.

## Users

Residents using an always-powered hallway wall tablet, usually for brief checks and adjustments a few times a day.

## Product Purpose

Read household device states and control the capabilities exposed by each integration from one panel.

## Operating Context

The panel stays on the home's Wi-Fi and must recover from connectivity loss, expired tokens and unattended reboots. A failed integration affects its devices without blocking the rest of the panel.

## Capabilities and Constraints

Yandex provides the reference device integration. Aqara lock information is read-only. Tuya recuperators use native LAN 3.4 with encrypted local keys. Xiaomi is a hosted-widget or launcher integration; the panel has no Xiaomi credentials. Domonap is launched through its own application, and incoming calls take priority over all panel content.

Visible adjustment controls must operate the advertised device capability. Ranges and precision come from vendor metadata. The panel distinguishes old device readings from recent successful polling and keeps command errors visible.

## Brand Commitments

Use the existing Domovoy app mark throughout the application in the selected lime and peach palette, preserving its geometry. The chosen visual direction is recorded in [DESIGN.md](DESIGN.md).

## Accessibility & Inclusion

Wall controls have touch targets of at least 64 dp. Text follows Android font scaling; status and errors are communicated in words as well as colour. Lock information never offers a write action. Domonap's incoming-call screen must not be covered, delayed or suppressed.
