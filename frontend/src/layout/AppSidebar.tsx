import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { Link, useLocation } from "react-router";
import {
    BoxCubeIcon,
    CalenderIcon,
    ChatIcon,
    ChevronDownIcon,
    GridIcon,
    GroupIcon,
    HorizontaLDots,
    ListIcon,
    UserCircleIcon,
} from "../icons";
import { useAuth, type AuthRole } from "../context/AuthContext";
import { useSidebar } from "../context/SidebarContext";

type RoleSinirliOge = {
    roller?: readonly AuthRole[];
    yakinda?: boolean;
};

type NavSubItem = RoleSinirliOge & {
    name: string;
    path?: string;
};

type NavItem = RoleSinirliOge & {
    name: string;
    icon: React.ReactNode;
    path?: string;
    subItems?: NavSubItem[];
};

const TALEP_OLUSTURABILEN_ROLLER = ["PERSONEL", "YONETICI", "HR"] as const;
const EKIPMAN_TALEBI_SAHIBI_ROLLERI = ["PERSONEL", "YONETICI", "HR", "ADMIN"] as const;
const REZERVASYON_ROLLERI = ["PERSONEL", "YONETICI", "HR", "ADMIN"] as const;
const EKIPMAN_KATALOGU_ROLLERI = [
    "PERSONEL",
    "YONETICI",
    "HR",
] as const;
const PERSONEL_YONETIM_ROLLERI = ["HR", "ADMIN"] as const;
const YONETICI_ROLU = ["YONETICI"] as const;
const ADMIN_ROLU = ["ADMIN"] as const;

const navItems: NavItem[] = [
    {
        icon: <GridIcon />,
        name: "Ana Sayfa",
        path: "/",
    },
    {
        icon: <CalenderIcon />,
        name: "Takvim",
        path: "/calendar",
    },
    {
        icon: <ChatIcon />,
        name: "Duyurular",
        yakinda: true,
    },
    {
        icon: <ListIcon />,
        name: "Talepler",
        subItems: [
            {
                name: "İzin Taleplerim",
                roller: TALEP_OLUSTURABILEN_ROLLER,
                path: "/talepler/izinlerim",
            },
            {
                name: "Ekipman Taleplerim",
                roller: EKIPMAN_TALEBI_SAHIBI_ROLLERI,
                path: "/talepler/ekipman",
            },
            {
                name: "Rezervasyon Taleplerim",
                roller: REZERVASYON_ROLLERI,
                path: "/talepler/rezervasyonlar",
            },
            {
                name: "Ekip İzin Talepleri",
                roller: YONETICI_ROLU,
                path: "/talepler/ekip-izinleri",
            },
            {
                name: "Tüm İzin Talepleri",
                roller: ADMIN_ROLU,
                path: "/talepler/tum-izinler",
            },
        ],
    },
    {
        icon: <BoxCubeIcon />,
        name: "Ekipmanlar",
        roller: EKIPMAN_KATALOGU_ROLLERI,
        yakinda: true,
    },
    {
        icon: <GroupIcon />,
        name: "Ekip Dizini",
        path: "/ekip-dizini",
    },
    {
        icon: <UserCircleIcon />,
        name: "Yönetim",
        subItems: [
            {
                name: "Personel Yönetimi",
                path: "/yonetim/personel",
                roller: PERSONEL_YONETIM_ROLLERI,
            },
            {
                name: "Katalog Yönetimi",
                path: "/yonetim/katalog",
                roller: PERSONEL_YONETIM_ROLLERI,
            },
            {
                name: "İzin ve Tatil Yönetimi",
                roller: PERSONEL_YONETIM_ROLLERI,
                yakinda: true,
            },
            {
                name: "Envanter ve Zimmet",
                roller: ADMIN_ROLU,
                path: "/yonetim/envanter-zimmet",
            },
            {
                name: "Audit Log",
                roller: ADMIN_ROLU,
                path: "/yonetim/audit-log",
            },
        ],
    },
];

function rolIzinliMi(
    roller: readonly AuthRole[] | undefined,
    rol: AuthRole,
): boolean {
    return roller === undefined || roller.includes(rol);
}

function menuyuRoleGoreFiltrele(
    items: NavItem[],
    rol: AuthRole,
): NavItem[] {
    return items.flatMap((item) => {
        if (!rolIzinliMi(item.roller, rol)) {
            return [];
        }

        if (!item.subItems) {
            return [item];
        }

        const subItems = item.subItems.filter((subItem) =>
            rolIzinliMi(subItem.roller, rol),
        );

        return subItems.length > 0 ? [{ ...item, subItems }] : [];
    });
}

const AppSidebar: React.FC = () => {
    const { isExpanded, isMobileOpen, isHovered, setIsHovered } = useSidebar();
    const { kullanici } = useAuth();
    const location = useLocation();

    const rol = kullanici?.rol;

    const gorunenNavItems = useMemo(
        () => (rol ? menuyuRoleGoreFiltrele(navItems, rol) : []),
        [rol],
    );

    const [openSubmenu, setOpenSubmenu] = useState<number | null>(null);
    const [subMenuHeight, setSubMenuHeight] = useState<Record<number, number>>({});
    const subMenuRefs = useRef<Record<number, HTMLDivElement | null>>({});

    const isActive = useCallback(
        (path: string) => location.pathname === path,
        [location.pathname],
    );

    useEffect(() => {
        const aktifSubmenuIndex = gorunenNavItems.findIndex((nav) =>
            nav.subItems?.some(
                (subItem) => subItem.path && isActive(subItem.path),
            ),
        );

        setOpenSubmenu(
            aktifSubmenuIndex === -1 ? null : aktifSubmenuIndex,
        );
    }, [gorunenNavItems, isActive]);

    useEffect(() => {
        if (openSubmenu === null) {
            return;
        }

        const submenu = subMenuRefs.current[openSubmenu];

        if (submenu) {
            setSubMenuHeight((oncekiYukseklikler) => ({
                ...oncekiYukseklikler,
                [openSubmenu]: submenu.scrollHeight,
            }));
        }
    }, [openSubmenu]);

    const handleSubmenuToggle = (index: number) => {
        setOpenSubmenu((oncekiAcikSubmenu) =>
            oncekiAcikSubmenu === index ? null : index,
        );
    };

    const renderMenuItems = (items: NavItem[]) => (
        <ul className="flex flex-col gap-4">
            {items.map((nav, index) => {
                const submenuAcik = openSubmenu === index;
                const menuMetniGorunur =
                    isExpanded || isHovered || isMobileOpen;

                return (
                    <li key={nav.name}>
                        {nav.subItems ? (
                            <button
                                type="button"
                                aria-expanded={submenuAcik}
                                onClick={() => handleSubmenuToggle(index)}
                                className={`menu-item group cursor-pointer ${
                                    submenuAcik
                                        ? "menu-item-active"
                                        : "menu-item-inactive"
                                } ${
                                    !isExpanded && !isHovered
                                        ? "lg:justify-center"
                                        : "lg:justify-start"
                                }`}
                            >
                <span
                    className={`menu-item-icon-size ${
                        submenuAcik
                            ? "menu-item-icon-active"
                            : "menu-item-icon-inactive"
                    }`}
                >
                  {nav.icon}
                </span>

                                {menuMetniGorunur && (
                                    <>
                                        <span className="menu-item-text">{nav.name}</span>
                                        <ChevronDownIcon
                                            className={`ml-auto h-5 w-5 transition-transform duration-200 ${
                                                submenuAcik
                                                    ? "rotate-180 text-brand-500"
                                                    : ""
                                            }`}
                                        />
                                    </>
                                )}
                            </button>
                        ) : nav.yakinda ? (
                            <div
                                aria-disabled="true"
                                aria-label={`${nav.name} - Yakında`}
                                title={`${nav.name} - Yakında`}
                                className={`menu-item relative cursor-not-allowed text-gray-400 dark:text-gray-500 ${
                                    !isExpanded && !isHovered
                                        ? "lg:justify-center"
                                        : "lg:justify-start"
                                }`}
                            >
                                <span className="menu-item-icon-size text-gray-400 dark:text-gray-500">
                                    {nav.icon}
                                </span>

                                {!isExpanded &&
                                    !isHovered &&
                                    !isMobileOpen && (
                                        <span
                                            aria-hidden="true"
                                            className="absolute right-1 top-1 rounded-full bg-gray-100 px-1 text-[9px] font-semibold text-gray-500 dark:bg-white/10 dark:text-gray-400"
                                        >
                                            Y
                                        </span>
                                    )}

                                {menuMetniGorunur && (
                                    <>
                                        <span className="menu-item-text">
                                            {nav.name}
                                        </span>
                                        <span className="ml-auto rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-gray-400">
                                            Yakında
                                        </span>
                                    </>
                                )}
                            </div>
                        ) : nav.path ? (
                            <Link
                                to={nav.path}
                                className={`menu-item group ${
                                    isActive(nav.path)
                                        ? "menu-item-active"
                                        : "menu-item-inactive"
                                }`}
                            >
                <span
                    className={`menu-item-icon-size ${
                        isActive(nav.path)
                            ? "menu-item-icon-active"
                            : "menu-item-icon-inactive"
                    }`}
                >
                  {nav.icon}
                </span>

                                {menuMetniGorunur && (
                                    <span className="menu-item-text">{nav.name}</span>
                                )}
                            </Link>
                        ) : null}

                        {nav.subItems && menuMetniGorunur && (
                            <div
                                ref={(element) => {
                                    subMenuRefs.current[index] = element;
                                }}
                                className="overflow-hidden transition-all duration-300"
                                style={{
                                    height: submenuAcik
                                        ? `${subMenuHeight[index] ?? 0}px`
                                        : "0px",
                                }}
                            >
                                <ul className="ml-9 mt-2 space-y-1">
                                    {nav.subItems.map((subItem) => (
                                        <li key={subItem.name}>
                                            {subItem.yakinda ? (
                                                <div
                                                    aria-disabled="true"
                                                    aria-label={`${subItem.name} - Yakında`}
                                                    title={`${subItem.name} - Yakında`}
                                                    className="menu-dropdown-item cursor-not-allowed text-gray-400 dark:text-gray-500"
                                                >
                                                    <span>{subItem.name}</span>
                                                    <span className="ml-auto rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-gray-400">
                                                        Yakında
                                                    </span>
                                                </div>
                                            ) : subItem.path ? (
                                                <Link
                                                    to={subItem.path}
                                                    className={`menu-dropdown-item ${
                                                        isActive(subItem.path)
                                                            ? "menu-dropdown-item-active"
                                                            : "menu-dropdown-item-inactive"
                                                    }`}
                                                >
                                                    {subItem.name}
                                                </Link>
                                            ) : null}
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}
                    </li>
                );
            })}
        </ul>
    );

    return (
        <aside
            className={`fixed left-0 top-0 z-50 mt-16 flex h-screen flex-col border-r border-gray-200 bg-white px-5 text-gray-900 transition-all duration-300 ease-in-out dark:border-gray-800 dark:bg-gray-900 lg:mt-0
        ${
                isExpanded || isMobileOpen
                    ? "w-[290px]"
                    : isHovered
                        ? "w-[290px]"
                        : "w-[90px]"
            }
        ${isMobileOpen ? "translate-x-0" : "-translate-x-full"}
        lg:translate-x-0`}
            onMouseEnter={() => !isExpanded && setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            <div
                className={`flex py-8 ${
                    !isExpanded && !isHovered
                        ? "lg:justify-center"
                        : "justify-start"
                }`}
            >
                <Link to="/">
                    {isExpanded || isHovered || isMobileOpen ? (
                        <>
                            <img
                                className="dark:hidden"
                                src="/images/logo/JFORCE-3.png"
                                alt="JForce"
                                width={150}
                                height={40}
                            />
                            <img
                                className="hidden dark:block"
                                src="/images/logo/JFORCE-3.png"
                                alt="JForce"
                                width={150}
                                height={40}
                            />
                        </>
                    ) : (
                        <img
                            src="/images/logo/jforce-icon.svg"
                            alt="JForce"
                            width={32}
                            height={32}
                        />
                    )}
                </Link>
            </div>

            <div className="no-scrollbar flex flex-col overflow-y-auto duration-300 ease-linear">
                <nav className="mb-6">
                    <div>
                        <h2
                            className={`mb-4 flex text-xs uppercase leading-[20px] text-gray-400 ${
                                !isExpanded && !isHovered
                                    ? "lg:justify-center"
                                    : "justify-start"
                            }`}
                        >
                            {isExpanded || isHovered || isMobileOpen ? (
                                "Menü"
                            ) : (
                                <HorizontaLDots className="size-6" />
                            )}
                        </h2>

                        {renderMenuItems(gorunenNavItems)}
                    </div>
                </nav>
            </div>
        </aside>
    );
};

export default AppSidebar;
