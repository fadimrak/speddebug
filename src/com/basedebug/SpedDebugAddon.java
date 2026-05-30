/*
 * Decompiled with CFR 0.152.
 */
package com.basedebug;

import com.basedebug.modules.SpedDebugCategory;
import com.basedebug.modules.SpedFinder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class SpedDebugAddon
extends MeteorAddon {
    public void onRegisterCategories() {
        Modules.registerCategory((Category)SpedDebugCategory.CATEGORY);
    }

    public void onInitialize() {
        Modules.getName().add((Module)new SpedFinder());
    }

    public String getPackage() {
        return "com.basedebug";
    }
}

