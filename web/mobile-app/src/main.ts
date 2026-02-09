import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

// Vant 4 按需引入
import {
  Button,
  Tabbar,
  TabbarItem,
  NavBar,
  Cell,
  CellGroup,
  Card,
  Form,
  Field,
  Toast,
  Dialog,
  Notify,
  Loading,
  PullRefresh,
  List,
  Empty,
  Tag,
  Swipe,
  SwipeItem,
  Grid,
  GridItem,
  ActionSheet,
  Picker,
  DatePicker,
  NumberKeyboard,
  Popup,
  Overlay,
  Divider,
  Icon,
  Image as VanImage,
  SwipeCell,
  Search,
  Tabs,
  Tab,
  Sticky,
  Collapse,
  CollapseItem,
  Badge,
  NoticeBar,
  CountDown,
  Progress,
  Circle,
  Skeleton,
  Lazyload,
  Cascader,
} from 'vant'

// Vant 样式
import 'vant/lib/index.css'

// 自定义样式
import './styles/main.css'

const app = createApp(App)

// 注册 Vant 组件
app.use(Button)
app.use(Tabbar)
app.use(TabbarItem)
app.use(NavBar)
app.use(Cell)
app.use(CellGroup)
app.use(Card)
app.use(Form)
app.use(Field)
app.use(Toast)
app.use(Dialog)
app.use(Notify)
app.use(Loading)
app.use(PullRefresh)
app.use(List)
app.use(Empty)
app.use(Tag)
app.use(Swipe)
app.use(SwipeItem)
app.use(Grid)
app.use(GridItem)
app.use(ActionSheet)
app.use(Picker)
app.use(DatePicker)
app.use(NumberKeyboard)
app.use(Popup)
app.use(Overlay)
app.use(Divider)
app.use(Icon)
app.use(VanImage)
app.use(SwipeCell)
app.use(Search)
app.use(Tabs)
app.use(Tab)
app.use(Sticky)
app.use(Collapse)
app.use(CollapseItem)
app.use(Badge)
app.use(NoticeBar)
app.use(CountDown)
app.use(Progress)
app.use(Circle)
app.use(Skeleton)
app.use(Lazyload)
app.use(Cascader)

app.use(createPinia())
app.use(router)

app.mount('#app')
