package com.shonkware.droidmodloader.engine.install

internal object InstallerOptionSelectionHelper {

    fun toggleOption(
        groups: List<InstallerGroup>,
        selectedOptionIds: Set<String>,
        optionId: String
    ): Set<String> {
        val group = groups.firstOrNull { installerGroup ->
            installerGroup.options.any { option -> option.id == optionId }
        } ?: return selectedOptionIds

        val option = group.options.firstOrNull { it.id == optionId }
            ?: return selectedOptionIds

        // Required options cannot be deselected; unusable options cannot be selected.
        if (option.required) {
            return selectedOptionIds + optionId
        }
        if (!option.type.isUsable) {
            return selectedOptionIds
        }

        val groupOptionIds = group.options.map { it.id }.toSet()
        val selected = selectedOptionIds.toMutableSet()
        val isSelected = optionId in selected

        return when (group.type) {
            InstallerGroupType.SELECT_EXACTLY_ONE -> {
                selected.removeAll(groupOptionIds)
                selected.add(optionId)
                selected
            }

            InstallerGroupType.SELECT_AT_MOST_ONE -> {
                selected.removeAll(groupOptionIds)

                if (!isSelected) {
                    selected.add(optionId)
                }

                selected
            }

            InstallerGroupType.SELECT_AT_LEAST_ONE -> {
                if (isSelected) {
                    val selectedInGroup = selected.count { it in groupOptionIds }

                    if (selectedInGroup <= 1) {
                        selected
                    } else {
                        selected.remove(optionId)
                        selected
                    }
                } else {
                    selected.add(optionId)
                    selected
                }
            }

            InstallerGroupType.SELECT_ALL -> {
                selected.add(optionId)
                selected
            }

            InstallerGroupType.SELECT_ANY -> {
                if (isSelected) {
                    selected.remove(optionId)
                } else {
                    selected.add(optionId)
                }

                selected
            }
        }.toSet()
    }

    /**
     * Repairs a selection after visibility changes: fills newly visible
     * SelectExactlyOne/SelectAll groups, enforces group limits, and keeps
     * required options selected.
     */
    fun normalizeSelection(
        groups: List<InstallerGroup>,
        selectedOptionIds: Set<String>
    ): Set<String> {
        val result = selectedOptionIds.toMutableSet()

        groups.forEach { group ->
            val usable = group.options.filter { it.type.isUsable }
            val usableIds = usable.map { it.id }.toSet()
            // A conditional type descriptor can make a previously selected
            // option unusable; drop it so it can never be installed.
            group.options.filterNot { it.type.isUsable }.forEach {
                result.remove(it.id)
            }
            val selected = result.filter { it in usableIds }

            when (group.type) {
                InstallerGroupType.SELECT_ALL -> result.addAll(usableIds)

                InstallerGroupType.SELECT_EXACTLY_ONE -> {
                    if (selected.size > 1) {
                        result.removeAll(selected.drop(1).toSet())
                    } else if (selected.isEmpty()) {
                        usable.firstOrNull()?.let { result.add(it.id) }
                    }
                }

                InstallerGroupType.SELECT_AT_MOST_ONE -> {
                    if (selected.size > 1) {
                        result.removeAll(selected.drop(1).toSet())
                    }
                }

                InstallerGroupType.SELECT_AT_LEAST_ONE,
                InstallerGroupType.SELECT_ANY -> {
                    group.options
                        .filter { it.required }
                        .forEach { result.add(it.id) }
                    if (group.type == InstallerGroupType.SELECT_AT_LEAST_ONE) {
                        val selectedInGroup = result.filter { it in usableIds }
                        if (selectedInGroup.isEmpty()) {
                            usable.firstOrNull()?.let { result.add(it.id) }
                        }
                    }
                }
            }
        }

        return result
    }
}
