package com.v2ray.ang.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

open class MyContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        /**
         * Wraps the context with a new locale.
         *
         * @param context The original context.
         * @param newLocale The new locale to set.
         * @return A ContextWrapper with the new locale.
         */
        fun wrap(context: Context, newLocale: Locale?): ContextWrapper {
            val locale = newLocale ?: Locale.getDefault()
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            Locale.setDefault(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
            configuration.setLayoutDirection(locale)

            return ContextWrapper(context.createConfigurationContext(configuration))
        }
    }
}
