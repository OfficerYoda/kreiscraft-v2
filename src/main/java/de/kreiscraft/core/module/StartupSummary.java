package de.kreiscraft.core.module;

import java.util.List;

public record StartupSummary(int enabled,int disabled,int failed,List<String>failedIds){}
