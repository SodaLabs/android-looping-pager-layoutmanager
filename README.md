[![CircleCI](https://circleci.com/gh/SodaLabs/android-looping-pager-layoutmanager.svg?style=svg)](https://circleci.com/gh/SodaLabs/android-looping-pager-layoutmanager) ![](https://img.shields.io/github/tag/android-looping-pager-layoutmanager/packageName.svg)

A `RecyclerView.LayoutManager` library enabling you the looping pager layout.

<p align="center">
  <img src="docs/demo.gif" width="320">
</p>

# Integration

### Gradle

```
dependencies {
	implementation 'com.github.SodaLabs:android-looping-pager-layoutmanager:x.x.x'
}
```

# Usage

```kotlin
recyclerView.setHasFixedSize(true) // Currently necessary for programmatic smooth scroll
recyclerView.layoutManager = LoopingPagerLayoutManager() // One line to enable infinite looping.
recyclerView.adapter = ...

// Enable the snap on fling through the plugable interface.
val snapHelper = PagerSnapHelper(resources.displayMetrics.density, 500)
snapHelper.attachToRecyclerView(recyclerView)
```

> Note: Currently, `setHasFixedSize(true)` is very necessary otherwise the library won't layout the item views correctly.

# Code Owners

* @boyw165
* @xuhaibahmad
* @kevinmmarlow
