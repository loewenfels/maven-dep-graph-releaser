function toggle(id) {
    var checkbox = document.getElementById(id)
    if (id.endsWith(':disableAll')) {
        toggleProject(checkbox, id)
    } else {
        toggleCommand(checkbox, id)
    }
}

function toggleProject(checkbox, id) {
    var checked = checkbox.checked
    var prefix = id.substr(0, id.indexOf(':disableAll'))
    iterate(prefix, (x, i) => {
        x.checked = checked
        if (isReleaseCommand(x) && !checked) {
            toggleCommand(x, prefix + ':' + i + ':disable')
        }
    })
}

function toggleCommand(checkbox, id) {
    var prefix = /(.*):[0-9]+:disable/.exec(id)[1]
    if (!checkbox.checked) {
        deactivateReleaseCommands(prefix, id)
        deactivateDependents(prefix)
    } else if (isReleaseCommand(checkbox)) {
        //can only activate release if all checkboxes are activated
        if (notAllChecked(prefix, id)) {
            checkbox.checked = false
        }
    }
}

function deactivateReleaseCommands(prefix, id){
    iterate(prefix, x => {
        if (x.id != id && isReleaseCommand(x)) {
            x.checked = false
        }
    })
}

function deactivateDependents(prefix) {
    var dependents = releasePlan[prefix]
    for (var i in dependents) {
        var disableAll = dependents[i] + ':disableAll'
        document.getElementById(disableAll).checked = false
        toggle(disableAll)
    }
}

function isReleaseCommand(checkbox) {
    return checkbox.classList.contains('release')
}

function notAllChecked(prefix, id) {
    var notAllChecked = false
    iterate(prefix, x => {
        if (x.id != id && !x.checked) {
            notAllChecked = true
            return true
        }
    })
    return notAllChecked
}

function iterate(prefix, act) {
    var found = true
    var i = 0
    while (found) {
        var checkbox = document.getElementById(prefix + ':' + i+ ':disable')
        found = checkbox != null
        if (found) {
            act(checkbox, i)
        }
        ++i
    }
}
