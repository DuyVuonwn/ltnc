// icons.js
// Script chuyển đổi Material Icons từ tên sang mã hex
(function () {
    'use strict';

    // Map tất cả icon từ các HTML đã cung cấp
    // Đã cập nhật đầy đủ cho: ImportAssets, ReportDamage, Liquidation, Reports, HandoverWindow, Stocktake
    const iconMap = {
        // Từ HTML đầu tiên
        'add_box': '&#xe146;',
        'local_hospital': '&#xe548;',
        'calendar_today': '&#xebcc;',
        'search': '&#xe8b6;',
        'expand_more': '&#xe5cf;',
        'unfold_more': '&#xe5d7;',
        'more_vert': '&#xe5d4;',
        'chevron_left': '&#xe5cb;',
        'chevron_right': '&#xe5cc;',
        'assignment_return': '&#xe862;',
        'delete_forever': '&#xe92b;',
        'category': '&#xe5f4;',
        'analytics': '&#xf1de;',
        'inventory': '&#xe179;',
        'history': '&#xea29;',
        'local_shipping': '&#xe558;',
        'settings': '&#xe8b8;',
        'trending_up': '&#xe8e5;',
        'warning': '&#xe002;',
        'check_circle': '&#xe86c;',
        'error': '&#xe000;',
        'info': '&#xe88e;',
        'edit': '&#xe3c9;',
        'delete': '&#xe872;',
        'visibility': '&#xe8f4;',
        'download': '&#xf090;',
        'upload': '&#xf09b;',
        'print': '&#xe8ad;',
        'filter_list': '&#xe152;',
        'sort': '&#xe164;',
        'refresh': '&#xe5d5;',
        'home': '&#xe88a;',
        'person': '&#xe7fd;',
        'group': '&#xe7ef;',
        'notifications': '&#xe7f4;',
        'help': '&#xe887;',
        'menu': '&#xe5d2;',
        'close': '&#xe5cd;',
        'arrow_back': '&#xe5c4;',
        'arrow_forward': '&#xe5c8;',
        'check': '&#xe5ca;',
        'cancel': '&#xe5c9;',
        'save': '&#xe161;',
        'add': '&#xe145;',
        'remove': '&#xe15b;',
        'folder': '&#xe2c7;',
        'insert_drive_file': '&#xe24d;',
        'description': '&#xe873;',
        'attachment': '&#xe2bc;',
        'link': '&#xe157;',
        'email': '&#xe0be;',
        'phone': '&#xe0cd;',
        'location_on': '&#xe55c;',
        'schedule': '&#xe8b5;',
        'lock': '&#xe897;',
        'vpn_key': '&#xe0da;',
        'visibility_off': '&#xe8f5;',
        'logout': '&#xe9ba;',

        // Từ HTML thứ hai (bàn giao tài sản)
        'assignment': '&#xe85d;',
        'tag': '&#xe9ef;',
        'apartment': '&#xeb44;',
        'inventory_2': '&#xe1a1;',

        // Từ HTML thứ ba (MedInventory)
        'health_and_safety': '&#xf1af;',
        'upload': '&#xe2c6;',
        'swap_horiz': '&#xe8d4;',
        'report_problem': '&#xf083;',
        'delete_sweep': '&#xe16c;',
        'bar_chart': '&#xe26b;',
        'tune': '&#xe429;',
        'folder_open': '&#xe2c8;',
        'build': '&#xe869;',
        'inventory_2': '&#xe1a1;',
        'check_circle': '&#xe86c;',
        'build': '&#xe869;',
        'expand_more': '&#xe5cf;',
        'person': '&#xe7fd;',
        'settings': '&#xe8b8;',
        'logout': '&#xe9ba;',
        'assignment_turned_in': '&#xe863;',
        'gpp_bad': '&#xf0c6;',
        'add_circle': '&#xe147;',
        'arrow_drop_down': '&#xe5c5;',
        'arrow_drop_up': '&#xe5c7;',
        'chair': '&#xf4a6;',
        'emergency': '&#xf108;',
        'fact_check': '&#xe8b2;',
        'laptop': '&#xe31e;',
        'list_alt': '&#xe8fe;',
        'medical_services': '&#xf109;',
        'medication_liquid': '&#xea87;',
        'monitor_heart': '&#xeaa2;',
        'qr_code_2': '&#xe00a;',
        'sell': '&#xe07e;',
        'vaccines': '&#xe138;',
        'move_up': '&#xeb64;',
        'radiology': '&#xe125;',
        'link_off': '&#xe16f;'
    };

    // Hàm chuyển đổi icon
    function convertIconsToHex() {
        const iconElements = document.querySelectorAll('.material-symbols-outlined');

        iconElements.forEach(element => {
            const iconName = element.textContent.trim();

            if (iconMap[iconName]) {
                element.innerHTML = iconMap[iconName];
                applyIconStyles(element);

                // Giữ lại các class CSS có sẵn
                if (element.classList.contains('text-sm')) {
                    element.style.fontSize = '20px';
                } else if (element.classList.contains('text-lg')) {
                    element.style.fontSize = '28px';
                } else if (element.classList.contains('text-xl')) {
                    element.style.fontSize = '32px';
                } else if (element.classList.contains('text-2xl')) {
                    element.style.fontSize = '40px';
                } else if (element.classList.contains('text-3xl')) {
                    element.style.fontSize = '48px';
                }
            }
        });
    }

    // Hàm áp dụng styles cho icon
    function applyIconStyles(element) {
        const styles = {
            fontFamily: "'Material Symbols Outlined', 'Material Symbols Outlined Fallback', sans-serif",
            fontWeight: 'normal',
            fontStyle: 'normal',
            fontSize: '24px',
            lineHeight: '1',
            letterSpacing: 'normal',
            textTransform: 'none',
            display: 'inline-block',
            whiteSpace: 'nowrap',
            wordWrap: 'normal',
            direction: 'ltr',
            fontFeatureSettings: "'liga'",
            WebkitFontSmoothing: 'antialiased',
            MozOsxFontSmoothing: 'grayscale',
            textRendering: 'optimizeLegibility'
        };

        Object.assign(element.style, styles);
    }

    // Hàm thêm CSS fallback
    function addFallbackCSS() {
        const styleId = 'material-icons-fallback-css';

        if (document.getElementById(styleId)) return;

        const style = document.createElement('style');
        style.id = styleId;
        style.textContent = `
            @font-face {
                font-family: 'Material Symbols Outlined Fallback';
                font-style: normal;
                font-weight: 100 700;
                src: url(https://fonts.gstatic.com/s/materialsymbolsoutlined/v192/kJF1BvYX7BgnkSrUwT8OhrdQw4oELdPIeeII9v6oDMzByHX9rA6RzaxHMPdY43zj-jCxv3fzvRNU22ZXGJpEpjC_1v-p_4MrImHCIJIZrDCvHOej.woff2) format('woff2');
            }
            
            .material-symbols-outlined {
                font-family: 'Material Symbols Outlined', 'Material Symbols Outlined Fallback', sans-serif;
                font-weight: normal;
                font-style: normal;
                line-height: 1;
                letter-spacing: normal;
                text-transform: none;
                display: inline-block;
                white-space: nowrap;
                word-wrap: normal;
                direction: ltr;
                font-feature-settings: 'liga';
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
            }
        `;
        document.head.appendChild(style);
    }

    // Hàm kiểm tra và tải font nếu cần
    function checkAndLoadFont() {
        // Kiểm tra xem font đã tải chưa
        const testElement = document.createElement('span');
        testElement.style.fontFamily = "'Material Symbols Outlined'";
        testElement.style.position = 'absolute';
        testElement.style.opacity = '0';
        testElement.style.pointerEvents = 'none';
        testElement.innerHTML = '&#xe145;'; // Icon "add"
        document.body.appendChild(testElement);

        setTimeout(() => {
            const isFontLoaded = testElement.offsetWidth > 0 && testElement.offsetHeight > 0;
            testElement.remove();

            if (!isFontLoaded) {
                console.warn('Material Icons font not loaded, using fallback system');
                addFallbackCSS();
                convertIconsToHex();
            }
        }, 100);
    }

    // Theo dõi DOM động
    function setupMutationObserver() {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.addedNodes.length) {
                    mutation.addedNodes.forEach((node) => {
                        if (node.nodeType === 1) {
                            if (node.classList && node.classList.contains('material-symbols-outlined')) {
                                convertIconsToHex();
                            } else if (node.querySelectorAll) {
                                const icons = node.querySelectorAll('.material-symbols-outlined');
                                if (icons.length) {
                                    convertIconsToHex();
                                }
                            }
                        }
                    });
                }
            });
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    // API công khai
    window.MaterialIconsHelper = {
        convert: convertIconsToHex,

        addIcon: function (name, hex) {
            iconMap[name] = hex;
            convertIconsToHex();
        },

        addIcons: function (icons) {
            Object.assign(iconMap, icons);
            convertIconsToHex();
        },

        getIconMap: function () {
            return { ...iconMap };
        },

        findIcon: function (name) {
            return iconMap[name];
        },

        getAvailableIcons: function () {
            return Object.keys(iconMap).sort();
        }
    };

    // Khởi tạo
    function init() {
        addFallbackCSS();
        checkAndLoadFont();
        convertIconsToHex();
        setupMutationObserver();

        // Thử chuyển đổi lại sau 1 giây để đảm bảo
        setTimeout(convertIconsToHex, 1000);
    }

    // Bắt đầu
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();