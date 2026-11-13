// AutoCore Application Scripts

document.addEventListener("DOMContentLoaded", function () {
    var panel = document.getElementById("formPanel");
    var showFormButton = document.getElementById("showForm");
    var closeFormButton = document.getElementById("closeForm");

    if (showFormButton) {
        showFormButton.addEventListener("click", function () {
            if (panel) {
                panel.classList.add("open");
            }
            var firstInput = panel ? panel.querySelector("input:not([type=hidden])") : null;
            if (firstInput) {
                firstInput.focus();
            }
        });
    }

    if (closeFormButton) {
        closeFormButton.addEventListener("click", function () {
            var params = new URLSearchParams(window.location.search);
            if (params.get("action") === "edit" || params.get("action") === "view") {
                window.location.href = window.location.pathname;
                return;
            }
            if (panel) {
                panel.classList.remove("open");
            }
        });
    }

    // Generic confirmation dialogs
    var confirmForms = document.querySelectorAll(".confirm-action");
    Array.prototype.forEach.call(confirmForms, function (form) {
        form.addEventListener("submit", function (event) {
            var promptMsg = form.getAttribute("data-confirm") || "Are you sure you want to proceed?";
            if (!window.confirm(promptMsg)) {
                event.preventDefault();
            }
        });
    });

    // Dynamic Items Builder for Purchases and Sales
    var itemsTable = document.getElementById("itemsTable");
    var addRowBtn = document.getElementById("addRowBtn");
    var grandTotalElem = document.getElementById("grandTotal");

    function updateGrandTotal() {
        if (!itemsTable || !grandTotalElem) return;
        var total = 0.0;
        var rows = itemsTable.querySelectorAll("tbody tr");
        Array.prototype.forEach.call(rows, function (row) {
            var qtyInput = row.querySelector(".item-qty");
            var priceInput = row.querySelector(".item-price");
            var lineTotalElem = row.querySelector(".item-linetotal");

            var qty = qtyInput ? parseFloat(qtyInput.value) || 0 : 0;
            var price = priceInput ? parseFloat(priceInput.value) || 0 : 0;
            var lineTotal = qty * price;

            if (lineTotalElem) {
                lineTotalElem.textContent = lineTotal.toFixed(2);
            }
            total += lineTotal;
        });
        grandTotalElem.textContent = total.toFixed(2);
    }

    if (itemsTable) {
        itemsTable.addEventListener("input", function (e) {
            if (e.target.classList.contains("item-qty") || e.target.classList.contains("item-price")) {
                updateGrandTotal();
            }
        });

        itemsTable.addEventListener("change", function (e) {
            if (e.target.classList.contains("item-select")) {
                var selectedOption = e.target.options[e.target.selectedIndex];
                var row = e.target.closest("tr");
                var priceInput = row ? row.querySelector(".item-price") : null;
                var stockElem = row ? row.querySelector(".item-avail-stock") : null;

                if (selectedOption && priceInput) {
                    var defaultPrice = selectedOption.getAttribute("data-price");
                    if (defaultPrice) {
                        priceInput.value = parseFloat(defaultPrice).toFixed(2);
                    }
                }
                if (selectedOption && stockElem) {
                    var availStock = selectedOption.getAttribute("data-stock");
                    stockElem.textContent = availStock !== null ? availStock : "-";
                }
                updateGrandTotal();
            }
        });

        itemsTable.addEventListener("click", function (e) {
            if (e.target.classList.contains("remove-row-btn")) {
                var rows = itemsTable.querySelectorAll("tbody tr");
                if (rows.length > 1) {
                    e.target.closest("tr").remove();
                    updateGrandTotal();
                } else {
                    alert("A transaction must contain at least one item line.");
                }
            }
        });
    }

    if (addRowBtn && itemsTable) {
        addRowBtn.addEventListener("click", function () {
            var tbody = itemsTable.querySelector("tbody");
            var firstRow = tbody.querySelector("tr");
            if (firstRow) {
                var newRow = firstRow.cloneNode(true);
                var inputs = newRow.querySelectorAll("input");
                Array.prototype.forEach.call(inputs, function (inp) {
                    if (inp.classList.contains("item-qty")) {
                        inp.value = "1";
                    } else if (inp.classList.contains("item-price")) {
                        inp.value = "0.00";
                    } else {
                        inp.value = "";
                    }
                });
                var lineTot = newRow.querySelector(".item-linetotal");
                if (lineTot) lineTot.textContent = "0.00";
                tbody.appendChild(newRow);
                updateGrandTotal();
            }
        });
    }

    // Initial total calculation on load
    updateGrandTotal();
});
