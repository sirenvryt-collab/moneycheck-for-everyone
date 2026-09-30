# Donut Balance Tracker

A client-side Fabric mod for Minecraft (1.21.1). DonutSMP removed its API, so
this mod tracks your money from **payment chat messages**:

- `Steve paid you $1,500` -> your amount goes **up** by 1,500
- `You paid Steve $1,500` -> your amount goes **down** by 1,500

Amounts like `2.5K`, `3M`, `1.2B` are understood too.

## Commands

- `/moneycheck` - shows how much you're up or down since midnight (and your total if you set one)
- `/moneycheck set <amount>` - set your starting balance, e.g. `/moneycheck set 2.5M`
- `/moneycheck reset` - reset today's change to $0

## Install

1. Download the latest `donut-balance-*.jar` from [Releases](../../releases).
2. Install [Fabric Loader](https://fabricmc.net/use/installer/) and [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
3. Put the jar in `.minecraft/mods` and launch.

Note: only payments are tracked. Money from selling, the shop, etc. is not
counted, so re-run `/moneycheck set` occasionally if you track your total.

## For you: turning this into a jar via GitHub (no local build needed)

This repo is set up so GitHub itself compiles the jar — you don't need
Gradle, Java, or a Minecraft dev environment on your own machine.

1. Create a new empty repo on GitHub (don't add a README there).
2. Push this folder to it:
   ```bash
   cd donut-balance-mod
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/donut-balance-mod.git
   git push -u origin main
   ```
3. Go to the **Actions** tab on GitHub — a "Build" workflow runs automatically
   and produces a downloadable jar as a build artifact.
4. To get a proper **Release** with the jar attached (what the download
   button on the website points to), tag a version and push the tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   The "Release" workflow builds the jar and attaches it to a new GitHub
   Release automatically.

Before pushing, do a find-and-replace of `YOUR_USERNAME` (in `README.md`,
`docs/index.html`, `fabric.mod.json`) with your actual GitHub username.

## Turning on the website

The `docs/` folder is a ready-to-go GitHub Pages site.

1. On GitHub, go to **Settings → Pages**.
2. Under "Build and deployment", set **Source** to "Deploy from a branch".
3. Set branch to `main` and folder to `/docs`, then save.
4. Your site will be live at `https://YOUR_USERNAME.github.io/donut-balance-mod/`
   within a minute or two.

## Local development (optional)

If you do want to build locally, you need a JDK 21 and Gradle installed:

```bash
gradle build
```

The jar appears in `build/libs/`.

## Project layout

```
src/main/java/com/donutbalance/mod/
  DonutBalanceClient.java   entrypoint, /moneycheck command, payment message parsing
  BalanceTracker.java       daily change + optional balance, saved to config
.github/workflows/
  build.yml                 builds a jar on every push
  release.yml               builds + attaches a jar when you push a vX.Y.Z tag
```

## Not affiliated with DonutSMP

This is an independent, unofficial fan tool. It is not endorsed by or affiliated with DonutSMP.
