function toggle(id) {
    if (id.endsWith(':disableAll')) {
        toggleProject(id)
    }
}

function toggleProject(id) {
    var checked = document.getElementById(id).checked
    var prefix = id.substr(0, id.indexOf(':disableAll'))
    var found = true
    var i = 0
    while (found) {
        var checkbox = document.getElementById(prefix + ':' + i+ ':disable')
        found = checkbox != null
        if (found) {
            checkbox.checked = checked
        }
        ++i
    }
}
