import React from 'react';
import Default from 'containers/Default/Default';
import { Login } from 'containers/Login/Login'
import { checkAuth, login } from 'scripts/restCalls';
import { LoginModal, LogoutModal } from 'components/Modal/Modal';

export default class App extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      admin: false,
      status: 'done',
      subDomain: 'default',
      style: false,
      modal: ''
    }

    this.NMSLogin = this.NMSLogin.bind(this);
    this.isAuthorised = this.isAuthorised.bind(this);
    this.toggleModal = this.toggleModal.bind(this);
  }

  componentDidMount(){
    this.isAuthorised();
  }

  NMSLogin(loginData){
    return login(loginData)
    .then( () => this.isAuthorised() )
    .then( status => {
      if(status != 200)  return "fail";
      return "success";
    })
    .catch( err => console.log(err) )
  }

  toggleModal(e){
    this.setState({
      modal: e.target.dataset.link.toString(),
      style: !this.state.style
    })    
  }

  isAuthorised(){
    return checkAuth()
    .then(status => {
      if(status == 200){
        this.setState({
          admin: true,
          status: 'done',
          subDomain: 'default',
        })
      }
      else{
        this.setState({
          status: 'done',
          subDomain: 'default',
        })
      }
      return status;
    })
    .catch(err => console.log(err));
  }

  render(){
    let page = null;
    switch(this.state.subDomain){
      case 'login':
        page = <Login nmsLogin={this.NMSLogin} />;
        break;
      case 'default':
        page = <Default 
                toggleModal={this.toggleModal} 
                admin={this.state.admin} />;
        break;
      default: 
        page = <Login nmsLogin={this.nmsLogin} />;
        break;
    }

    let modal = null;
    switch(this.state.modal){
      case 'sign-in':
        modal = <LoginModal
                  toggleModal={this.toggleModal}
                  style={this.state.style} 
                  nmsLogin={this.NMSLogin}/>
        break;
      case 'sign-out':
        modal = <LogoutModal 
                  toggleModal={this.toggleModal}
                  style={this.state.style} 
                  isAuthorised={this.isAuthorised} />
        break;
      default:
        modal = "";
        break;
    }
    
    return (
      <div className='app-component'>
        { (this.state.status == 'done') ? page :  "" }
        { modal }
      </div>    
    );
  }
}